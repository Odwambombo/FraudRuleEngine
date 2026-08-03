# Fraud Rule Engine observability alerts

Grafana provisions three Loki-backed alert rules from
`grafana/provisioning/alerting`:

- any `APPLICATION_EXCEPTION` event in five minutes;
- any `FRAUD_PROCESSING_FAILURE` event in five minutes;
- more than four `AUTHENTICATION_FAILURE` events in five minutes.

The `alert-webhook` Compose service is a development-only receiver. It prints
Grafana webhook payloads to its container logs so local delivery can be tested
without using or committing a real endpoint or credential. It is not a
production notification service and must not be deployed outside local
development.

The `grafana/otel-lgtm` image and `alert-webhook` service in `compose.yaml` are
both local-development components. The provisioning files can also be mounted
into a self-hosted Grafana instance, but file provisioning is not supported by
Grafana Cloud. For Grafana Cloud, recreate these rules, contact point, and
notification routing with its supported API or Terraform provider. See
[Grafana's file-provisioning documentation](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/)
for the supported model and overwrite behavior.

`notification-policy.yaml` defines the complete notification-policy tree for
Grafana organization 1. Provisioning it overwrites that tree. In an existing
organization, merge the fraud receiver route into the organization-owned policy
source instead of mounting this file unchanged.

Start the local stack and inspect received notifications with:

```shell
docker compose up --build --wait
docker compose logs -f alert-webhook
```

In Grafana, open **Alerting > Contact points**, select
`fraud-rule-engine-webhook`, and send a test notification. The receiver returns
HTTP 204 and its logs show `Grafana alert webhook received` followed by the
payload. This verifies only Grafana-to-webhook delivery. To exercise the full
application-to-OpenTelemetry-to-Loki-to-rule path, use the invalid-login test in
the main [README](../README.md#test-the-provisioned-alerts-locally).

For a self-hosted staging or production Grafana instance, inject these values
into the Grafana process from that platform's secret/configuration system. They
are Grafana provisioning inputs, not Fraud Rule Engine ECS task variables. For
Grafana Cloud, create equivalent resources with its API or Terraform provider
instead of setting these variables on the application:

```shell
GRAFANA_ALERT_WEBHOOK_URL=https://alerts.example.com/grafana
GRAFANA_ALERT_WEBHOOK_AUTH_SCHEME=Bearer
GRAFANA_ALERT_WEBHOOK_AUTH_CREDENTIALS=<secret-token>
GRAFANA_ALERT_ENVIRONMENT=production
```

`GRAFANA_ALERT_WEBHOOK_AUTH_CREDENTIALS` is provisioned through Grafana's
encrypted `secureSettings`. Omit both authentication variables when the webhook
does not require bearer authentication.

`GRAFANA_ALERT_ENVIRONMENT` labels the resulting alert for routing; it does not
scope the current Loki queries. The supplied selectors filter only
`service_name` and therefore assume a separate Loki/Grafana deployment or tenant
per environment. If one Loki tenant receives multiple environments, add the
`deployment_environment_name` label to each selector—and verify the exported
label name—before deployment.
