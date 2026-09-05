# Fraud Rule Engine

An explainable transaction risk-assessment service built with Java 21 and
Spring Boot 4.1. It receives already-categorized transaction events, evaluates
every enabled fraud rule, stores the transaction and assessment atomically,
and exposes the saved decisions through a versioned REST API.

## Prerequisites

- Docker Engine or Docker Desktop with Docker Compose v2 and support for
  `docker compose up --wait` is sufficient for the complete containerized run.
- Java 21 is required when running the backend directly with Gradle.
- Node.js 22 and npm are required for frontend development, Storybook, and
  Playwright outside the application image.
- The copied API and observability commands use `curl` and `jq`.
- The AWS CLI is needed only for the AWS Secrets Manager and deployment
  examples.

## Run with Docker

Build the application image and start it together with PostgreSQL, the local
observability stack, and a development-only alert webhook receiver:

```shell
docker compose up --build --wait
```

The Fraud Rule Engine web console and API are available at
`http://localhost:8080`, and Grafana is available at
`http://localhost:3000`. The Docker image builds both the Vue frontend and the
Spring Boot backend. Compose waits for PostgreSQL and the observability
collector to become healthy before starting the application. Liquibase applies
the local database changelog, after which Hibernate validates the JPA mappings
against the resulting schema.

Local authentication is enabled. Open the web console and use one of these
development-only accounts:

| User | Password | Access |
| --- | --- | --- |
| `local-admin` | `local-admin-change-me` | Transaction writes, assessment reads, docs, and operations |
| `local-operator` | `local-operator-change-me` | Transaction writes only |
| `local-analyst` | `local-analyst-change-me` | Assessment reads only |

The credentials and local JWT signing key can be overridden with the
`FRAUD_LOCAL_*` environment variables defined in `application.properties`.
The local issuer is development-only and is excluded from the `staging`,
`production`, and `migration` profiles; never use these defaults as hosted
credentials.

Local role and permission overrides use the Java enum names: `ADMIN`,
`OPERATOR`, `ANALYST`, `AUDITOR`, and `TRANSACTION_WRITE`,
`ASSESSMENT_READ`, `OPERATIONS_READ`, `DOCS_READ`. Local JWTs carry those same
enum names in their `roles` and `permissions` claims; local authentication does
not depend on Cognito group names or OAuth scope strings.

| Service | Local address | Purpose |
| --- | --- | --- |
| Fraud Rule Engine | `http://localhost:8080` | Web console, REST API, and `/livez`/`/readyz` probes |
| Private management | `http://localhost:8081` | Loopback-only Actuator and Prometheus endpoints |
| PostgreSQL | `localhost:5432` | Local application database |
| Grafana | `http://localhost:3000` | Metrics, logs, and trace exploration |
| OTLP/HTTP | `http://localhost:4318` | Telemetry from an app running on the host |
| OTLP/gRPC | `localhost:4317` | Optional gRPC telemetry ingestion |
| Alert webhook | Compose network only | Prints local Grafana test notifications; never deploy it to a hosted environment |

To follow the application logs or stop the stack:

```shell
docker compose logs --follow app
docker compose down
```

`docker compose down` keeps the PostgreSQL and observability data volumes. Use
`docker compose down --volumes` when you intentionally want to delete the
local database, metrics, traces, and logs and start clean.

To build only the application image:

```shell
docker build --tag fraud-rule-engine:local .
```

To run that image against PostgreSQL and the observability collector exposed
on the host (for example, after
`docker compose up --detach postgres observability`):

```shell
docker run --rm \
  --name fraud-rule-engine \
  --publish 127.0.0.1:8080:8080 \
  --add-host host.docker.internal:host-gateway \
  --env SPRING_PROFILES_ACTIVE=local \
  --env SERVER_ADDRESS=0.0.0.0 \
  --env FRAUD_DB_URL=jdbc:postgresql://host.docker.internal:5432/fraud_rule_engine \
  --env FRAUD_DB_USERNAME=fraud \
  --env FRAUD_DB_PASSWORD=fraud \
  --env OTEL_EXPORTER_OTLP_ENDPOINT=http://host.docker.internal:4318 \
  --env OTEL_LOGS_EXPORTER=otlp \
  --env OTEL_SERVICE_NAME=fraud-rule-engine \
  --env OTEL_DEPLOYMENT_ENVIRONMENT=local \
  fraud-rule-engine:local
```

For any other PostgreSQL instance, change the three `FRAUD_DB_*` values to
match that database.

## Run the application locally

Start PostgreSQL and the local OpenTelemetry/Grafana backend:

```shell
docker compose up --detach --wait postgres observability
```

Start the application and point its standard OTLP endpoint at the local
collector:

```shell
SPRING_PROFILES_ACTIVE=local \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
OTEL_SERVICE_NAME=fraud-rule-engine \
OTEL_DEPLOYMENT_ENVIRONMENT=local \
./gradlew bootRun
```

Swagger UI is a local developer aid. With the exact `local` profile, open
`http://localhost:8080/swagger-ui.html` or retrieve the OpenAPI document from
`http://localhost:8080/v3/api-docs` without a token. The documented transaction
and assessment operations remain JWT-protected: call
`POST /api/v1/auth/login`, copy the returned `accessToken`, select **Authorize**
in Swagger UI, and paste the token. Swagger supplies the `Bearer` prefix.
Both documentation endpoints are disabled in staging and production. Any
non-local or mixed-profile run that explicitly re-enables them still requires
the `DOCS_READ` permission.

For frontend development with live reload, keep the backend on port `8080` and
run Vue separately:

```shell
cd src/frontend
npm ci
npm run serve
```

Open `http://localhost:5173`. The development server proxies `/api`, `/livez`,
and `/readyz` to the backend, so browser CORS exceptions are not needed. If the
backend uses another port, set `FRAUD_API_PROXY_TARGET`, for example
`FRAUD_API_PROXY_TARGET=http://localhost:18080 npm run serve`.

### Frontend component and browser tests

Storybook documents the authentication, assessment-result, permission, empty,
loading, and failure states without requiring the backend. Start its interactive
component catalogue at `http://localhost:6006`:

```shell
cd src/frontend
npm ci
npm run storybook
```

Build every story and render it in Chromium, including the local-login
interaction test and failing-on-violation accessibility checks, with one
self-contained command:

```shell
npm run test-storybook:ci
```

The Playwright suite exercises the real application through the browser. It
checks a rejected login, admin login and logout, an authenticated fraud
assessment and history lookup, and the operator's write-only authorization.
Start the Compose stack, install Chromium once, and run the suite:

```shell
FRAUD_APP_PORT=18080 docker compose up --build --wait

cd src/frontend
npm ci
npm run test:e2e:install
PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 npm run test:e2e
```

The browser tests use the local admin and operator accounts listed above. Set
`E2E_ADMIN_USERNAME`, `E2E_ADMIN_PASSWORD`, `E2E_OPERATOR_USERNAME`, and
`E2E_OPERATOR_PASSWORD` if those credentials were overridden. Failed runs retain
screenshots, video, and traces under `src/frontend/test-results`; the HTML report
is written to `src/frontend/playwright-report`.

Database settings can be overridden with `FRAUD_DB_URL`,
`FRAUD_DB_USERNAME`, and `FRAUD_DB_PASSWORD`.

If the collector is intentionally not running, disable remote telemetry for
that run to avoid exporter connection warnings:

```shell
OTEL_TRACES_EXPORTER=none OTEL_METRICS_EXPORTER=none OTEL_LOGS_EXPORTER=none ./gradlew bootRun
```

## Application profiles

The application has three long-running service profiles plus a one-shot
`migration` profile. The default configuration is for local development.
Hosted `staging` and `production` can load database settings from their
environment-specific AWS secret and require JWT security settings.

| Profile | Intended use | API security | Swagger | Health details |
| --- | --- | --- | --- | --- |
| `local` | Local application development | Local JWT login required | Public UI/contract; documented business calls still require JWT | Hidden |
| `staging` | Integration and pre-production testing | Cognito JWT required | Disabled | Hidden anonymously; shown for any authenticated user |
| `production` | Live deployment | JWT required | Disabled | Hidden |
| `staging,migration` / `production,migration` | One-shot schema migration | Non-web; security disabled | Not available | Not applicable |

Use the explicit `local` profile for developer runs. Documentation access is
fail-closed when no profile or more than one profile is active.

Docker Compose selects `local` by default, supplies its PostgreSQL connection
settings, enables the local JWT issuer, and enables Liquibase startup migration:

```shell
docker compose up --build --wait
```

If port `8080` is already in use, select another host port while the
application continues to listen on `8080` inside its container:

```shell
FRAUD_APP_PORT=18080 docker compose up --build --wait
```

To exercise the production profile with Compose locally, replace the example
database password and every Cognito placeholder. Hosted profiles deliberately
do not fall back to the local identity provider. If the local PostgreSQL volume
was initialized with different credentials, remove it first; this permanently
deletes that local database. This is only a local production-profile smoke
test: Compose deliberately enables startup Liquibase for its disposable
database and does not reproduce the hosted one-shot migration topology.

```shell
docker compose down --volumes

SPRING_PROFILES_ACTIVE=production \
OTEL_DEPLOYMENT_ENVIRONMENT=production \
FRAUD_DB_USERNAME=fraud_prod \
FRAUD_DB_PASSWORD='replace-with-a-secret' \
FRAUD_DB_POOL_MAX_SIZE=10 \
FRAUD_DB_POOL_MIN_IDLE=2 \
FRAUD_OAUTH2_ISSUER_URI=https://cognito-idp.af-south-1.amazonaws.com/REPLACE_WITH_USER_POOL_ID \
FRAUD_OAUTH2_AUDIENCE=REPLACE_WITH_PUBLIC_SPA_CLIENT_ID \
FRAUD_COGNITO_DOMAIN=https://REPLACE_WITH_DOMAIN_PREFIX.auth.af-south-1.amazoncognito.com \
FRAUD_COGNITO_CLIENT_ID=REPLACE_WITH_PUBLIC_SPA_CLIENT_ID \
FRAUD_SCOPE_TRANSACTION_WRITE=fraud-rule-engine/fraud.transactions.write \
FRAUD_SCOPE_ASSESSMENT_READ=fraud-rule-engine/fraud.assessments.read \
FRAUD_SCOPE_OPERATIONS_READ=fraud-rule-engine/fraud.operations.read \
FRAUD_SCOPE_DOCS_READ=fraud-rule-engine/fraud.docs.read \
docker compose up --build --wait
```

PostgreSQL only applies its `POSTGRES_*` settings during initial database
creation. In a real production deployment, supply `SPRING_PROFILES_ACTIVE`
and the three `FRAUD_DB_*` values through the platform's secret/configuration
system rather than committing them to the repository.

To run a profile outside Docker, provide the database connection explicitly.
The example assumes the schema has already been migrated; for a fresh database,
first run the same settings with `SPRING_PROFILES_ACTIVE=staging,migration` and
require exit code `0`.

```shell
SPRING_PROFILES_ACTIVE=staging \
FRAUD_DB_URL=jdbc:postgresql://localhost:5432/fraud_rule_engine \
FRAUD_DB_USERNAME=fraud \
FRAUD_DB_PASSWORD=fraud \
FRAUD_OAUTH2_ISSUER_URI=https://cognito-idp.af-south-1.amazonaws.com/REPLACE_WITH_USER_POOL_ID \
FRAUD_OAUTH2_AUDIENCE=REPLACE_WITH_PUBLIC_SPA_CLIENT_ID \
FRAUD_COGNITO_DOMAIN=https://REPLACE_WITH_DOMAIN_PREFIX.auth.af-south-1.amazoncognito.com \
FRAUD_COGNITO_CLIENT_ID=REPLACE_WITH_PUBLIC_SPA_CLIENT_ID \
FRAUD_SCOPE_TRANSACTION_WRITE=fraud-rule-engine/fraud.transactions.write \
FRAUD_SCOPE_ASSESSMENT_READ=fraud-rule-engine/fraud.assessments.read \
FRAUD_SCOPE_OPERATIONS_READ=fraud-rule-engine/fraud.operations.read \
FRAUD_SCOPE_DOCS_READ=fraud-rule-engine/fraud.docs.read \
./gradlew bootRun
```

The same Docker image is used for both staging and production; the active
profile is selected when the container starts, so changing profiles does not
require a different Dockerfile.

### AWS Secrets Manager

The `staging` and `production` profiles can load their database settings from
AWS Secrets Manager. An AWS application host is not required: while
developing, the application can call Secrets Manager from this computer using
credentials configured for the AWS CLI.

The default secret names and region are:

| Profile | Secret name | Region |
| --- | --- | --- |
| `staging` | `/fraud-rule-engine/staging` | `af-south-1` |
| `production` | `/fraud-rule-engine/production` | `af-south-1` |

Each secret must be a JSON secret with these top-level keys:

```json
{
  "url": "jdbc:postgresql://database-host:5432/fraud_rule_engine",
  "username": "fraud_app",
  "password": "replace-with-the-database-password"
}
```

The import adds the `spring.datasource.` prefix, so these become
`spring.datasource.url`, `spring.datasource.username`, and
`spring.datasource.password`. When the secret is available, those values
override the local `FRAUD_DB_*` fallback.

Create the two secrets in the AWS console or with the AWS CLI. After signing
in locally, export the staging Cognito variables listed under **Production
security and deployment controls**, then start staging without putting database
credentials in the shell:

```shell
aws sso login --profile fraud-dev

AWS_PROFILE=fraud-dev \
AWS_REGION=af-south-1 \
SPRING_PROFILES_ACTIVE=staging \
./gradlew bootRun
```

Override `AWS_STAGING_SECRET_NAME`, `AWS_PRODUCTION_SECRET_NAME`, or
`AWS_REGION` if the secrets are stored elsewhere. Secret imports are marked
optional while the AWS environment is being prepared, so the existing
`FRAUD_DB_*` environment-variable configuration remains a fallback.

For any hosted staging or production service or migration task, make that
environment's secret mandatory. For production, set:

```text
SPRING_CONFIG_IMPORT=aws-secretsmanager:/fraud-rule-engine/production?prefix=spring.datasource.
```

The IAM user or role running the application needs
`secretsmanager:GetSecretValue` for its environment's secret and, when the
secret uses a customer-managed KMS key, `kms:Decrypt` for that key. Prefer an
IAM task/pod/instance role when the application is eventually hosted on AWS;
do not store AWS access keys in this repository.

## Production security and deployment controls

Hosted staging and production are OAuth 2.0 resource servers. The web console
uses Amazon Cognito Managed Login with authorization code and PKCE; Cognito,
not this application, creates users and issues JWTs. Configure the issuer,
public app-client ID, managed-login domain, full custom scopes, and Cognito
groups through the documented `FRAUD_*` environment variables. Transaction
writes and assessment reads require both a mapped permission and an approved
group role. The API accepts Cognito access tokens and rejects Cognito ID tokens.
Local Docker uses a separate self-contained JWT issuer and fixed development
users. That issuer and its login endpoint are excluded by profile from
`staging`, `production`, and `migration`; hosted profiles always select Cognito.
Hosted servlet processes also fail startup if `fraud.security.enabled` resolves
to `false`, including through an environment-variable override. The one-shot
`staging,migration` and `production,migration` tasks remain valid because they
run as non-web processes.

Authorization policy is code-owned by the `FraudRole` and `FraudPermission`
enums, and controllers reference those enums rather than OAuth strings.
Staging and production map their explicitly configured Cognito scopes and
groups to stable internal authorities such as `PERMISSION_TRANSACTION_WRITE`
and `ROLE_ADMIN`; unknown provider values do not become application
authorities.

Self-signup is supported but hidden by default. Even when enabled, a new user
receives no fraud role until an administrator assigns a Cognito group. See
[`docs/aws-production-readiness.md`](docs/aws-production-readiness.md) for the
exact roles, callbacks, and environment settings.

### Application rate limiting

The application applies two token-bucket limits to `/api/v1/**` requests. The
general API policy allows 1,200 requests per minute with a 100-request-per-second
burst limit. The local login endpoint has an independent limit of 10 requests
per minute and 3 per second. Authenticated API callers are keyed by principal;
anonymous callers and login attempts are keyed by the client address resolved by
the servlet container. CORS preflight requests and non-API surfaces such as
`/livez`, `/readyz`, Actuator, static assets, and Swagger are not limited.

Rejected requests return `429 Too Many Requests`, `Retry-After`,
`X-RateLimit-Limit`, and `X-RateLimit-Remaining`, with the normal structured API
error body. Tune or disable the defaults with:

| Setting | Default |
| --- | --- |
| `FRAUD_RATE_LIMIT_ENABLED` | `true` |
| `FRAUD_API_RATE_LIMIT_PER_MINUTE` | `1200` |
| `FRAUD_API_RATE_LIMIT_PER_SECOND` | `100` |
| `FRAUD_LOGIN_RATE_LIMIT_PER_MINUTE` | `10` |
| `FRAUD_LOGIN_RATE_LIMIT_PER_SECOND` | `3` |
| `FRAUD_RATE_LIMIT_MAX_CLIENT_BUCKETS` | `100000` |
| `FRAUD_RATE_LIMIT_CLIENT_IDLE_TIME` | `10m` |

Buckets are bounded and expire after inactivity, but they are local to one JVM.
In a multi-task deployment, retain the existing WAF rate rule as the shared
perimeter control and treat the application limiter as defense in depth. Tune
both layers with representative traffic before production rollout.

### Test browser authentication locally

The default Compose run shows the local username/password login and issues a
short-lived, signed JWT after successful authentication. The browser keeps it
in `sessionStorage`, and both the HTTP security filter and controller
`@PreAuthorize` checks enforce its enum-named permissions and roles.

To test the hosted Cognito flow instead, create a development Cognito user
pool, resource server, and public SPA client. The application itself can still
run on this computer; an AWS app host is not required. Register
`http://localhost:8080/` as both the callback and logout URL (or use the exact
alternate host port you selected), then start:

```shell
SPRING_PROFILES_ACTIVE=staging \
OTEL_DEPLOYMENT_ENVIRONMENT=staging \
FRAUD_SECURITY_ENABLED=true \
FRAUD_OAUTH2_ISSUER_URI=https://cognito-idp.af-south-1.amazonaws.com/REPLACE_WITH_USER_POOL_ID \
FRAUD_OAUTH2_AUDIENCE=REPLACE_WITH_PUBLIC_SPA_CLIENT_ID \
FRAUD_COGNITO_DOMAIN=https://REPLACE_WITH_DOMAIN_PREFIX.auth.af-south-1.amazoncognito.com \
FRAUD_COGNITO_CLIENT_ID=REPLACE_WITH_PUBLIC_SPA_CLIENT_ID \
FRAUD_COGNITO_SIGNUP_ENABLED=false \
FRAUD_SCOPE_TRANSACTION_WRITE=fraud-rule-engine/fraud.transactions.write \
FRAUD_SCOPE_ASSESSMENT_READ=fraud-rule-engine/fraud.assessments.read \
FRAUD_SCOPE_OPERATIONS_READ=fraud-rule-engine/fraud.operations.read \
FRAUD_SCOPE_DOCS_READ=fraud-rule-engine/fraud.docs.read \
docker compose up --build --wait
```

Open `http://localhost:8080` and continue through Cognito. Assign the test user
to `FRAUD_OPERATOR`, `FRAUD_ANALYST`, `FRAUD_AUDITOR`, or `FRAUD_ADMIN`, then
sign in again so the new `cognito:groups` claim appears in the access token.
If self-signup is enabled in the Cognito pool, also set
`FRAUD_COGNITO_SIGNUP_ENABLED=true`; registration creates the account but does
not grant a fraud role.

Actuator is served on the separate management port `8081` in every servlet
profile. Compose binds that port to loopback; a hosted deployment must keep it
private with network controls. `/livez` and `/readyz` are the health probes on
application port `8080`. Hosted staging and production web tasks default
Liquibase to disabled; the deployment pipeline must first run the same image
once with `staging,migration` or `production,migration` and require exit code
`0`.

The repository also includes a Terraform production-controls module for an
existing AWS ALB/ECS/RDS stack. It creates immutable and scan-on-push ECR,
rate-limiting and managed-rule WAF protection, WAF logs, an SNS alarm topic,
and ALB, ECS, and RDS CloudWatch alarms. See
[`docs/aws-production-readiness.md`](docs/aws-production-readiness.md) for the
complete authentication, migration, connection-pool, release, and AWS
configuration, and [`infra/terraform/README.md`](infra/terraform/README.md) for
the module inputs and environment examples.

## OpenTelemetry and Grafana

Spring Boot instruments incoming HTTP requests and publishes standard JVM
metrics.
The application also records fraud-specific metrics such as processed and
flagged transactions, processing duration, idempotent replays, failures, and
rule matches. The telemetry flow is:

```text
Fraud Rule Engine -- OTLP/HTTP --> OpenTelemetry Collector
                                      |-- metrics --> Prometheus
                                      |-- traces  --> Tempo
                                      `-- logs    --> Loki
Prometheus + Tempo + Loki ------------------------> Grafana
```

`spring-boot-starter-opentelemetry` supplies tracing, while Micrometer's OTLP
registry exports metrics. The existing `/actuator/prometheus` endpoint remains
available for direct inspection. Docker Compose uses Grafana's `otel-lgtm`
image, which packages and preconfigures the collector, Prometheus, Tempo,
Loki, Grafana, and useful JVM and RED dashboards for local development.
Logback continues to write logs to standard output for `docker compose logs`
and also sends them through OpenTelemetry to Loki. Logs emitted while a trace
is active carry the trace and span context, so Grafana can move from the log
record to its Tempo trace.

Completed transaction assessments emit a structured, privacy-safe log named
`Transaction assessment completed`. It includes the assessment ID, risk score,
risk level, flagged status, matched-rule count, and replay/created status. It
does not include the customer ID, amount, merchant, or other transaction data.

Operational failures emit three stable, privacy-safe alert markers that can be
matched identically in Loki and CloudWatch Logs:

| Signal | Log marker | Prometheus counter |
| --- | --- | --- |
| Unhandled application exception | `APPLICATION_EXCEPTION` | `application_exceptions_unhandled_total` |
| Fraud-processing failure | `FRAUD_PROCESSING_FAILURE` | `fraud_transactions_processing_failures_total` |
| Authentication failure | `AUTHENTICATION_FAILURE` | `security_authentication_failures_total` |
| API rate-limit rejection | `API rate limit exceeded` | `fraud_api_rate_limit_rejected_total` |

The alert records include a failure-type value, usually an exception's simple
class name, but never exception messages, usernames, request paths, transaction
payloads, credentials, bearer tokens, or stack traces. Authentication failures
include rejected bearer authentication and bad local-development credentials;
an authenticated `403` authorization denial is deliberately not counted as an
authentication failure.

The standard `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable selects the
collector. Compose uses `http://observability:4318` because containers address
each other by service name; an application started by Gradle uses
`http://localhost:4318`. Spring Boot appends the signal-specific
`/v1/traces`, `/v1/metrics`, and `/v1/logs` paths. Set
`OTEL_LOGS_EXPORTER=none` for a run where remote log export should be disabled;
console logging remains available.

Local and staging runs sample every trace, publish metrics every 10 seconds,
and flush log batches every 2 seconds so tests are deterministic. Production
samples 10% of traces, publishes metrics every 60 seconds, and flushes logs
every 5 seconds. Logs themselves are not trace-sampled: a log can still reach
Loki when its surrounding trace was not sampled. These defaults can be
overridden at runtime with `OTEL_TRACES_SAMPLER_ARG`,
`OTEL_METRIC_EXPORT_INTERVAL`, and
`MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_SCHEDULE_DELAY`. Set
`OTEL_SERVICE_NAME`, `OTEL_SERVICE_VERSION`, and
`OTEL_DEPLOYMENT_ENVIRONMENT` to identify a deployment in Grafana.
For example, `OTEL_TRACES_SAMPLER_ARG=0.25` samples 25% of traces, while
`OTEL_METRIC_EXPORT_INTERVAL=30000` uses a 30,000-millisecond interval.

The bundled LGTM container is for local development and testing, not a
production deployment. When hosting the staging or production application,
point the same `OTEL_EXPORTER_OTLP_ENDPOINT` setting at a hosted collector or
observability provider; the application code and Docker image do not change.
If that endpoint requires authentication, supply
`OTEL_EXPORTER_OTLP_HEADERS` from the deployment's secret store rather than
committing credentials to this repository.

### Test the complete configuration locally

Start everything. If port `8080` is already occupied, use the second command
and use `http://localhost:18080` as `APP_URL` below.

```shell
docker compose up --build --wait
```

Alternatively, when host port `8080` is busy:

```shell
FRAUD_APP_PORT=18080 docker compose up --build --wait
```

Check the application and Grafana, then generate a unique fraud assessment:

```shell
APP_URL=http://localhost:8080
MANAGEMENT_URL=http://localhost:8081
GRAFANA_URL=http://localhost:3000

curl --fail --silent --show-error "$APP_URL/readyz"
curl --fail --silent --show-error "$GRAFANA_URL/api/health"

ACCESS_TOKEN=$(curl --fail --silent --show-error \
  --request POST "$APP_URL/api/v1/auth/login" \
  --header 'Content-Type: application/json' \
  --data '{"username":"local-admin","password":"local-admin-change-me"}' \
  | jq --raw-output '.accessToken')

RUN_ID=$(date +%s)
curl --include --request POST "$APP_URL/api/v1/transaction-events" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "{
    \"eventId\": \"evt-otel-${RUN_ID}\",
    \"transactionId\": \"txn-otel-${RUN_ID}\",
    \"customerId\": \"cust-otel-${RUN_ID}\",
    \"amount\": 25000.00,
    \"currency\": \"ZAR\",
    \"category\": \"ELECTRONICS\",
    \"transactionType\": \"CARD_PURCHASE\",
    \"merchant\": \"Tech World\",
    \"country\": \"ZA\",
    \"customerCountry\": \"ZA\",
    \"transactionTime\": \"2026-07-27T02:15:00\"
  }"
```

The request should return `201 Created`, score `60`, risk level `HIGH`, and
`flagged: true`. Confirm that the application itself recorded the custom
metrics:

```shell
curl --fail --silent --show-error "$MANAGEMENT_URL/actuator/prometheus" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  | grep -E 'fraud_transactions|fraud_rules_matches|http_server_requests'
```

Allow about 10 seconds for the telemetry batches to be published, then verify
all three backends through Grafana's preconfigured data sources:

```shell
curl --get --fail --silent --show-error \
  "$GRAFANA_URL/api/datasources/proxy/uid/prometheus/api/v1/query" \
  --data-urlencode 'query=fraud_transactions_processed_total'

curl --get --fail --silent --show-error \
  "$GRAFANA_URL/api/datasources/proxy/uid/tempo/api/search" \
  --data-urlencode 'q={ resource.service.name = "fraud-rule-engine" }'

curl --get --fail --silent --show-error \
  "$GRAFANA_URL/api/datasources/proxy/uid/loki/loki/api/v1/query_range" \
  --data-urlencode 'query={service_name="fraud-rule-engine"} |= "Transaction assessment completed"' \
  --data-urlencode 'since=15m' \
  --data-urlencode 'limit=20'
```

For visual verification, open `http://localhost:3000`; anonymous local admin
access is enabled by Compose. In **Dashboards**, open **JVM Metrics** or
**RED Metrics (classic histogram)** and select `fraud-rule-engine`. In
**Explore**:

- Select **Prometheus** and query `fraud_transactions_processed_total`,
  `fraud_transactions_flagged_total`, or
  `sum by (rule) (fraud_rules_matches_total)`.
- Select **Tempo** and run
  `{ resource.service.name = "fraud-rule-engine" }`, then open the POST span
  to inspect its duration, HTTP status, and trace attributes.
- Select **Loki**, run
  `{service_name="fraud-rule-engine"} |= "Transaction assessment completed"`,
  and expand a result to inspect the structured fraud fields. Use its trace ID
  link to open the correlated request in Tempo.

Use `docker compose logs --follow app observability` if an exporter or backend
does not become ready. Stop the stack with `docker compose down`.

### Test the provisioned alerts locally

Grafana loads three Loki-backed rules from
`observability/grafana/provisioning/alerting`: any application exception, any
fraud-processing failure, and five or more authentication failures during a
five-minute window. They route to the provisioned
`fraud-rule-engine-webhook` contact point. Its default destination is the
development-only `alert-webhook` container, which returns a successful response
and prints each notification payload to its container logs.

Confirm that Grafana loaded the rules and contact point:

```shell
curl --fail --silent --show-error \
  "$GRAFANA_URL/api/v1/provisioning/alert-rules" \
  | jq --raw-output '.[].title'

curl --fail --silent --show-error \
  "$GRAFANA_URL/api/v1/provisioning/contact-points" \
  | jq --raw-output '.[].name'
```

The safest end-to-end trigger is a set of intentionally invalid local login
attempts. It exercises the real application log exporter, Loki query, Grafana
rule, notification policy, and webhook without adding a failure-only API:

```shell
for attempt in 1 2 3 4 5; do
  curl --silent --output /dev/null \
    --request POST "$APP_URL/api/v1/auth/login" \
    --header 'Content-Type: application/json' \
    --data '{"username":"local-admin","password":"intentionally-wrong"}'
done

docker compose logs --follow alert-webhook
```

Allow for the application's two-second log batch, Grafana's one-minute rule
interval, and ten-second notification grouping. The receiver output should
contain `Grafana alert webhook received` and the
`Repeated authentication failures` alert. You can also select the contact
point under **Alerting > Contact points** in Grafana and send a test
notification directly. See [`observability/README.md`](observability/README.md)
for contact-point settings. The test alert resolves after the five-minute log
window expires and the next one-minute rule evaluation completes.

The bundled LGTM and webhook images remain local-development components. For
self-hosted staging or production Grafana, load the provisioning files and
inject `GRAFANA_ALERT_WEBHOOK_URL`, its optional authorization values, and
`GRAFANA_ALERT_ENVIRONMENT` into the Grafana process from that platform's
secret/configuration system—not into the Fraud Rule Engine task.
The policy file owns Grafana's complete root notification-policy tree, so merge
it with any existing organization routes before deployment. Grafana Cloud does
not accept file provisioning; create equivalent resources with its supported
API or Terraform provider instead. AWS alert delivery is independent: set the
Terraform module's `application_log_group_name` and subscribe a confirmed
incident destination to its SNS topic.

`GRAFANA_ALERT_ENVIRONMENT` labels the provisioned alerts but does not filter
their Loki queries. The supplied rules assume one Loki/Grafana deployment or
tenant per environment. If environments share a Loki tenant, add the verified
`deployment_environment_name` label to every selector before deployment.

## Process a transaction

```shell
ACCESS_TOKEN=$(curl --fail --silent --show-error \
  --request POST http://localhost:8080/api/v1/auth/login \
  --header 'Content-Type: application/json' \
  --data '{"username":"local-admin","password":"local-admin-change-me"}' \
  | jq --raw-output '.accessToken')

curl --request POST http://localhost:8080/api/v1/transaction-events \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{
    "eventId": "evt-10001",
    "transactionId": "txn-50001",
    "customerId": "cust-123",
    "amount": 25000.00,
    "currency": "ZAR",
    "category": "ELECTRONICS",
    "transactionType": "CARD_PURCHASE",
    "merchant": "Tech World",
    "country": "ZA",
    "customerCountry": "ZA",
    "transactionTime": "2026-07-27T02:15:00"
  }'
```

A new event returns `201 Created`. Replaying the same `eventId` returns the
original assessment with `200 OK` and does not evaluate or store the event
again. Concurrent submissions are retried across serializable transactions so
the same guarantee holds under races. Reusing an `eventId` with different
transaction data returns `409 EVENT_ID_CONFLICT`.

`customerCountry` is optional. When supplied, it represents the customer's
registered country and enables the foreign-transaction rule. Transaction
times are local, offset-free values because that is the upstream event
contract; unusual-time evaluation therefore uses the supplied local time.
Event, transaction, and customer IDs must be 1–100 characters, begin with a
letter or number, and otherwise contain only letters, numbers, dots,
underscores, colons, or hyphens.

## Rules and scoring

| Rule | Match | Score |
| --- | --- | ---: |
| `HIGH_VALUE_TRANSACTION` | ZAR amount greater than 20,000 | 40 |
| `UNUSUAL_TRANSACTION_TIME` | Local time from 00:00 inclusive to 04:00 exclusive | 20 |
| `FOREIGN_TRANSACTION` | Transaction country differs from supplied customer country | 25 |
| `TRANSACTION_VELOCITY` | Current event is the fifth or later event in an inclusive 10-minute window | 35 |
| `RISKY_TRANSACTION_CATEGORY` | Category is `GAMBLING` or `CRYPTOCURRENCY` | 15 |

All thresholds, scores, enabled flags, categories, and the velocity window are
configured under `fraud` in `application.properties`.

Risk levels are `LOW` (0–29), `MEDIUM` (30–59), `HIGH` (60–79), and
`CRITICAL` (80+). Assessments are flagged at a score of 60 or more.

## Retrieval API

- `GET /api/v1/fraud-assessments/{assessmentId}`
- `GET /api/v1/fraud-assessments/transaction/{transactionId}`
- `GET /api/v1/fraud-assessments?page=0&size=20`

The collection supports optional `customerId`, `riskLevel`, `flagged`, `from`,
and `to` filters. `from` and `to` accept either ISO-8601 offset date-times or
date-only values such as `2026-07-01`; date-only bounds use UTC calendar days.
They apply to the assessment evaluation timestamp. Results have a
deterministic newest-first ordering and a maximum page size of 100.

## Verification

```shell
./gradlew test
```

Fast tests use H2 while applying the same Liquibase changelog. The PostgreSQL
integration test uses Testcontainers and skips when Docker is unavailable.
Local and staging expose Actuator health, info, metrics, and Prometheus on port
`8081`; production exposes health, info, and Prometheus only. Non-health
endpoints require `OPERATIONS_READ`, and hosted network controls must keep the
management port private.

The [GitHub Actions CI workflow](.github/workflows/ci.yml) lints the frontend,
renders and tests every Storybook story, runs the backend tests, builds the
environment-neutral application image, and executes Playwright against that
image for every push, pull request, or manually dispatched run. It verifies the
image without publishing it.

The [k6 load-test guide](load-tests/README.md) provides a repeatable local,
authenticated transaction-ingestion scenario with executable thresholds. The
[measured local baseline](load-tests/results/2026-08-03-local-20rps.md) records
the test conditions, pass/fail result, database verification, and limitations;
the adjacent JSON file is the sanitized machine-readable k6 summary.
