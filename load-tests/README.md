# Load testing

The k6 scenario in `k6/transaction-ingestion.js` exercises the application's
real write path:

1. authenticate once through the local login endpoint;
2. send a unique transaction with the returned bearer token;
3. evaluate the fraud rules; and
4. persist the transaction, assessment, and matched-rule results in PostgreSQL.

It uses an
[open-model, constant-arrival-rate workload](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/).
This keeps attempting the configured arrival rate even if the application
slows down. The run fails with a non-zero exit code when any of these
[thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/) is missed:

- more than 99% of transactions must be created successfully;
- HTTP failures must remain below 1%;
- transaction-ingestion p95 latency must remain below `P95_MS` (500 ms by
  default); and
- k6 must not drop any scheduled iterations.

## Run locally with Docker

Start the complete local stack first:

```bash
FRAUD_APP_PORT=18080 FRAUD_MANAGEMENT_PORT=18081 docker compose up --build --detach --wait
docker compose ps
```

Then run the 20 requests/second, 60-second reference profile:

```bash
docker run --rm \
  --add-host host.docker.internal:host-gateway \
  --volume "$PWD/load-tests:/work" \
  --workdir /work \
  --env BASE_URL=http://host.docker.internal:18080 \
  --env RUN_ID="local-$(date -u +%Y%m%dT%H%M%SZ)" \
  --env SUMMARY_PATH=/work/results/latest-summary.json \
  grafana/k6:2.0.0 run k6/transaction-ingestion.js
```

The current scenario is intentionally local-only: setup always calls
`POST /api/v1/auth/login`, which is absent from staging and production.
Override `USERNAME` and `PASSWORD` only when the configured local credentials
have changed. Before targeting a hosted environment, extend the scenario to
acquire or securely accept a short-lived Cognito access token; never enable or
reuse the built-in local credentials there.

Useful overrides are `RATE`, `DURATION`, `PRE_ALLOCATED_VUS`, `MAX_VUS`, and
`P95_MS`. For example, use this short smoke profile before a longer run:

```bash
docker run --rm \
  --add-host host.docker.internal:host-gateway \
  --volume "$PWD/load-tests:/work" \
  --workdir /work \
  --env BASE_URL=http://host.docker.internal:18080 \
  --env RATE=5 \
  --env DURATION=10s \
  --env RUN_ID="smoke-$(date -u +%Y%m%dT%H%M%SZ)" \
  --env SUMMARY_PATH=/work/results/smoke-summary.json \
  grafana/k6:2.0.0 run k6/transaction-ingestion.js
```

Each successfully created request persists database records with a `load-`
identifier prefix. The test deliberately does not delete them because cleanup
traffic would distort the measured path. Reset all local volumes after
reviewing the evidence only if you do not need any local data:

```bash
docker compose down --volumes
```

That command deletes both the local PostgreSQL and observability volumes.

## Interpreting the result

The terminal summary reports completed and dropped iterations, successful
writes, HTTP failures, and response-time percentiles. The documented Docker
commands write the machine-readable k6 summary under `results/` through
`SUMMARY_PATH`. The custom exporter removes `setup_data` before writing it, so
the short-lived authentication token is not stored in the artifact.

The checked-in [local evidence report](results/2026-08-03-local-20rps.md) and
[sanitized raw summary](results/2026-08-03-local-20rps-summary.json) show one
measured reference run. A local result establishes a repeatable baseline; it is
not a production capacity claim. Production sizing requires a representative
dataset, network path, ECS task count and limits, RDS class/configuration, and a
controlled non-production AWS environment.
