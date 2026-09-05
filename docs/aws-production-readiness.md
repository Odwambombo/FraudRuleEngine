# AWS production-readiness guide

This guide describes the controls included in this repository and how to use
them when the application runs as an Amazon ECS Fargate service. Docker
Compose remains a local-development topology: RDS replaces `postgres`, and a
managed observability backend—reached directly or through an OpenTelemetry
Collector or Grafana Alloy—replaces the local `observability` container.

## API authentication and authorization

In staging and production, the application is an OAuth 2.0 resource server. It
validates signed JWT access tokens from Amazon Cognito and does not store
hosted passwords or issue hosted tokens. Local development instead uses an
embedded, profile-restricted JWT issuer with three fixed development users so
the same authentication and authorization path can be tested without AWS.
That local issuer and login controller cannot load under `staging`,
`production`, or `migration`.

Security is enabled by default for local, staging, and production. Hosted
profiles pin the provider to Cognito and fail startup when security is disabled
or when the issuer or audience is missing. This is a fail-closed invariant:
setting `FRAUD_SECURITY_ENABLED=false` on a staging or production servlet
process prevents that process from starting. The migration task is deliberately
excluded because it runs with `spring.main.web-application-type=none`.
Configure each ECS service with:

```text
FRAUD_SECURITY_ENABLED=true
FRAUD_OAUTH2_ISSUER_URI=https://cognito-idp.<region>.amazonaws.com/<user-pool-id>
FRAUD_OAUTH2_AUDIENCE=<app-client-id-or-api-audience>
FRAUD_SCOPE_TRANSACTION_WRITE=<resource-server>/fraud.transactions.write
FRAUD_SCOPE_ASSESSMENT_READ=<resource-server>/fraud.assessments.read
FRAUD_SCOPE_OPERATIONS_READ=<resource-server>/fraud.operations.read
FRAUD_SCOPE_DOCS_READ=<resource-server>/fraud.docs.read
FRAUD_COGNITO_DOMAIN=https://<domain-prefix>.auth.<region>.amazoncognito.com
FRAUD_COGNITO_CLIENT_ID=<public-spa-app-client-id>
FRAUD_COGNITO_SIGNUP_ENABLED=false
```

The profile-level Secrets Manager imports are optional only to keep developer
workstations flexible. Each hosted ECS service and migration task should make
its database secret mandatory, for example:

```text
SPRING_CONFIG_IMPORT=aws-secretsmanager:/fraud-rule-engine/production?prefix=spring.datasource.
```

Use the staging path in staging. The JSON secret must contain top-level `url`,
`username`, and `password` keys. Grant the task role
`secretsmanager:GetSecretValue` on only that environment's secret and
`kms:Decrypt` when it uses a customer-managed KMS key. The Terraform module in
this repository does not create the database secret or task-role permission.

The audience validator supports standard `aud` claims and Cognito access
tokens that identify their client with `client_id`. Every accepted token must
contain `token_use=access`; ID tokens and tokens without that claim are
rejected.

| Permission enum | External OAuth scope | Required role / Cognito group | Protected resource |
| --- | --- | --- | --- |
| `TRANSACTION_WRITE` | `fraud.transactions.write` | `OPERATOR` / `FRAUD_OPERATOR`, or `ADMIN` / `FRAUD_ADMIN` | `POST /api/v1/transaction-events` |
| `ASSESSMENT_READ` | `fraud.assessments.read` | `ANALYST` / `FRAUD_ANALYST`, `AUDITOR` / `FRAUD_AUDITOR`, or `ADMIN` / `FRAUD_ADMIN` | `GET /api/v1/fraud-assessments/**` |
| `OPERATIONS_READ` | `fraud.operations.read` | Scope-controlled machine/operator access | Actuator endpoints other than health, including Prometheus |
| `DOCS_READ` | `fraud.docs.read` | Scope-controlled developer access | Defense-in-depth if documentation is deliberately re-enabled outside local |

The role-to-permission relationship is defined once in the Java enums, and
controllers refer only to `FraudPermission`/`FraudRole` values. OAuth scope
strings do not belong to the Java domain or controller policy. The obsolete
`FRAUD_ROLES_TRANSACTION_WRITE` and `FRAUD_ROLES_ASSESSMENT_READ` environment
variables are therefore no longer used. At the staging/production identity-
provider boundary, explicitly configured Cognito scopes map to stable
`PERMISSION_*` authorities and known Cognito groups map to stable `ROLE_*`
authorities. Unknown scopes and groups are ignored rather than becoming
application privileges.

Amazon Cognito prefixes a custom scope with its resource-server identifier.
For example, an identifier of `fraud-rule-engine` produces
`fraud-rule-engine/fraud.transactions.write`; configure the matching complete
value in the ECS environment.

Allow only transaction-write and assessment-read on the public browser app
client. Keep operations-read and the reserved docs-read permission on separate
operator or confidential machine clients; otherwise any browser user could
request those scopes directly from the authorization endpoint. Swagger UI and
the OpenAPI document are enabled only for the exact local profile and are
disabled in staging and production. If an operator deliberately re-enables
them outside local for controlled diagnostics, `DOCS_READ` remains mandatory.

Unauthenticated protected requests return `401`. Business requests require
both the permission scope and one of the configured Cognito groups; a valid
token that lacks either returns `403`. Unknown routes are denied rather than
falling through under an authenticated identity.

The same requirements are enforced twice: request rules reject unauthorized
HTTP traffic, and controller `@PreAuthorize` annotations protect direct method
invocation. Local JWTs carry enum names in dedicated `permissions` and `roles`
claims. The local converter turns those values into the same internal
authorities as the Cognito adapter, so local tests exercise the application
authorization policy without depending on hosted-provider strings.

## Browser login and user registration

The Vue frontend is built into the same Docker image and served from `/`, so
its API calls are same-origin and need no permissive CORS configuration. It
loads non-secret identity settings from `GET /api/v1/frontend-config` at
runtime, allowing the same image digest to move from staging to production.

For hosted environments, use a Cognito public app client with no client
secret, authorization-code flow only, and PKCE. Configure each environment's
exact HTTPS application URL as both an allowed callback and sign-out URL. The
frontend redirects the browser to Cognito Managed Login for sign-in or
registration, validates OAuth state, exchanges the code with its PKCE verifier,
and sends only the access token to the API. ID tokens are used only for display
identity and are rejected by the backend API.

Here, *public client* is OAuth terminology for a client that cannot keep a
secret; it does not mean the console must be internet-accessible. The supplied
Terraform enables TOTP but leaves MFA optional, so users can still authenticate
without TOTP. Require MFA for production internal fraud operations, or federate
to a corporate identity provider that enforces it. Corporate SAML/OIDC
federation is not created by this module.

Self-registration is disabled by default. Setting
`FRAUD_COGNITO_SIGNUP_ENABLED=true` displays the Create account action, but a
new account intentionally has no fraud permissions until an administrator
assigns it to an approved Cognito group. Do not automatically assign broad
fraud roles to arbitrary public registrations.

`ASSESSMENT_READ` intentionally grants approved internal fraud staff access to
the application-wide assessment pool; this is not tenant/customer-scoped.
Cognito group assignment and access reviews are therefore security controls.
The application does not yet persist an immutable per-user audit trail of
assessment views or actions; add one before real production use. If external
users are ever introduced, add tenant/customer claims and query-level ownership
enforcement.

The frontend's role and permission checks improve the user experience by
hiding actions the token cannot perform. They are not a security boundary:
Spring Security independently validates the JWT signature, issuer, audience,
and provider-specific permission and role claims before a controller is
called.

## Public and private HTTP surfaces

Use an internal ALB or private access path for this internal fraud-operations
console unless internet exposure is explicitly approved. Port `8080` serves the
SPA and static assets, public `/api/v1/frontend-config`, `/livez`, `/readyz`,
and the JWT-protected business APIs. An API-only deployment may omit routing the
SPA assets, but the bundled console needs `/` and its same-origin routes.

Every long-running servlet profile runs Actuator on a dedicated port `8081`;
the non-web `migration` profile exposes no HTTP server:

```text
management.server.port=8081
```

Do not create a public ALB listener or public security-group rule for this
port. Permit it only from private monitoring and administrative clients; the
ALB uses `/readyz` on port `8080` and does not need `8081`. Prometheus also
requires a valid bearer token carrying `OPERATIONS_READ` at the application
layer. The Terraform module does not provision the separate trusted client or
token-acquisition flow a scraper needs. Health details are hidden in production;
staging hides them anonymously and shows them when any valid JWT is supplied.

Recommended ECS checks:

```text
Container liveness:  GET http://localhost:8080/livez
ALB readiness:       GET http://<task>:8080/readyz
Private Prometheus:  GET http://<task>:8081/actuator/prometheus + operations bearer token
```

Readiness includes database connectivity; liveness deliberately does not.
This removes an instance from traffic when it cannot process a transaction but
does not restart every task during a shared RDS outage.

## Database query safety

Runtime persistence never constructs SQL from request text. Spring Data
derived queries bind their method arguments, explicit JPQL uses named
parameters, and collection filtering uses Criteria predicates with fixed
entity attributes. Pagination ordering is also server-defined; the API does
not accept a database property or SQL fragment as a sort expression.

Identifier-shaped API inputs share the `@ApiIdentifier` validation contract:
one to 100 URL-safe characters. This reduces malformed input and keeps values
aligned with the database schema, but parameter binding remains the SQL
injection control. Do not add SQL-keyword blacklists, strip apostrophes, or
manually escape values. Legitimate free text, including merchant names with
apostrophes, remains valid and is persisted as bound data.

If dynamic sorting or filtering is added later, map a small API enum to fixed
entity attributes and continue binding every value. Never pass a request value
to `JpaSort.unsafe`, concatenate it into JPQL/native SQL, or treat it as a
column name.

## One-shot database migration

Long-running staging and production services default Liquibase to disabled.
Run the same release image as a one-shot ECS task before updating the service:

```text
SPRING_PROFILES_ACTIVE=production,migration
SPRING_CONFIG_IMPORT=aws-secretsmanager:/fraud-rule-engine/production?prefix=spring.datasource.
```

Use `staging,migration` and the staging secret for staging. Keep `migration`
last in the active-profile list. The migration profile itself disables remote
metrics, traces, and logs while retaining console logs. This profile:

1. starts without an HTTP server;
2. applies Liquibase changes;
3. lets Hibernate validate the resulting schema;
4. logs a successful completion; and
5. closes the context so the ECS task exits with code `0`.

If migration or validation fails, Spring startup fails and the task exits
nonzero. The deployment pipeline must wait for the task to reach `STOPPED`,
require exit code `0`, and only then update the ECS service. Normal service
tasks use only `staging` or `production`, with
`SPRING_LIQUIBASE_ENABLED=false`.

Use backward-compatible expand/migrate/contract changes while old and new
tasks overlap. For stronger separation, give the migration task a
schema-changing database credential and give service tasks a DML-only
credential.

## Database connection pools

Hikari limits are explicit per application task:

| Setting | Staging default | Production default | Override |
| --- | ---: | ---: | --- |
| Maximum pool size | 5 | 10 | `FRAUD_DB_POOL_MAX_SIZE` |
| Minimum idle | 1 | 2 | `FRAUD_DB_POOL_MIN_IDLE` |
| Connection timeout | 5 seconds | 5 seconds | `FRAUD_DB_CONNECTION_TIMEOUT_MS` |
| Validation timeout | 3 seconds | 3 seconds | `FRAUD_DB_VALIDATION_TIMEOUT_MS` |
| Idle timeout | 10 minutes | 10 minutes | `FRAUD_DB_IDLE_TIMEOUT_MS` |
| Maximum lifetime | 30 minutes | 30 minutes | `FRAUD_DB_MAX_LIFETIME_MS` |
| Keepalive | 2 minutes | 2 minutes | `FRAUD_DB_KEEPALIVE_TIME_MS` |

Before changing task counts or pool sizes, ensure:

```text
(maximum ECS tasks × pool maximum) + migration/admin headroom
    < the RDS connection budget
```

Tune these values with load tests and the selected RDS instance, not only CPU
capacity.

## ECR, WAF, and CloudWatch controls

The Terraform module in `infra/terraform` adds controls around an existing
ALB/ECS/RDS deployment. Use a separate module instance and Terraform state for
staging and production.

It creates:

- an encrypted ECR repository with immutable tags, scan-on-push, and retention;
- a REGIONAL WAF web ACL associated with the supplied ALB;
- a per-IP rate-based blocking rule;
- per-principal application token buckets for defense in depth inside each JVM;
- AWS managed Common and Known Bad Inputs rule groups;
- WAF logs with authorization and cookie header redaction;
- an SNS alarm topic; and
- ALB/target 5xx, p95 latency, unhealthy-target, ECS CPU/memory, RDS
  CPU/storage/memory/connection/read/write-latency alarms, application-log
  alarms, and an RDS event subscription for availability and backup events.

WAF log redaction does not redact the separately retained sampled-request view.
Disable sampled requests for production or apply an approved data-handling
policy; the Terraform production example disables them.

Set `application_log_group_name` to the ECS task's existing CloudWatch log
group so Terraform creates metric filters for the application's stable
`APPLICATION_EXCEPTION`, `FRAUD_PROCESSING_FAILURE`, and
`AUTHENTICATION_FAILURE` messages. Leaving it `null` intentionally omits those
three log-derived alarms. The RDS event subscription publishes native database
events directly to the same SNS topic; the `backup` category includes routine
start/completion events as well as problems, so route or filter informational
events before paging an on-call engineer.

Start managed WAF rules in count mode in staging, inspect legitimate requests,
then enforce the rules. Treat the example rate and alarm thresholds as starting
points that must be calibrated with representative traffic. The application
rate limiter is local to each ECS task, so it does not replace WAF's shared
perimeter enforcement; configure `FRAUD_API_RATE_LIMIT_PER_MINUTE`,
`FRAUD_API_RATE_LIMIT_PER_SECOND`, and the WAF window together.

See [`infra/terraform/README.md`](../infra/terraform/README.md) for variables,
staging and production examples, encryption notes, and outputs.

## Release sequence

1. Run all application and security tests.
2. Build once for the ECS task definition's declared CPU architecture, or build
   a multi-architecture manifest. Set `APP_VERSION=<git-sha>` and
   `VCS_REF=<git-sha>` and record the resulting manifest digest.
3. Push the Git-SHA tag to the immutable staging ECR repository and enforce the
   organization's vulnerability-severity gate.
4. Run the staging migration task with that digest and require exit code `0`.
5. Deploy that digest to staging and run authentication, API, WAF, and
   observability smoke tests.
6. Obtain production approval.
7. Copy or replicate the exact OCI image into production ECR, verify the
   destination manifest digest matches the tested staging manifest, and enforce
   the production scan gate. Do not rebuild it.
8. Run the production migration task using that production-accessible digest.
9. Deploy the same digest to the production ECS service. Use the ECS deployment
   circuit breaker and CloudWatch alarms to roll back a failed service
   deployment. Database changes still require a compatible roll-forward or an
   explicitly tested database rollback.

The checked-in GitHub Actions workflow currently tests and builds the image
only. It does not push to ECR, poll scan findings, create an SBOM or signature,
run migrations, deploy ECS, or perform rollback automation.

Terraform creates preventive and detective controls around existing hosting;
it cannot deploy an empty AWS account. It does not create the VPC, subnets,
security groups, ALB listener/TLS/DNS, ECS task definition or service,
deployment circuit breaker or autoscaling, RDS instance/backups, Secrets
Manager secret, task/execution IAM roles, application log group, OTLP
collector/backend, SNS recipients, or deployment pipeline. ECR scan-on-push is
enabled, but the module does not enforce a severity gate, SBOM, or signature.

Production readiness also requires account isolation, least-privilege IAM,
private RDS networking, backups and restore tests, a confirmed incident route
subscribed to the SNS topic, a production Grafana contact point when
Grafana/Loki is used, representative load tests, threat modelling, immutable
user-access audit records, and a documented recovery procedure.
