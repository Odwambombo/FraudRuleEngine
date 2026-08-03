import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Rate } from 'k6/metrics';

function positiveInteger(name, fallback) {
  const rawValue = __ENV[name] || String(fallback);
  const value = Number.parseInt(rawValue, 10);

  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer; received '${rawValue}'`);
  }

  return value;
}

const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const username = __ENV.USERNAME || 'local-admin';
const password = __ENV.PASSWORD || 'local-admin-change-me';
const rate = positiveInteger('RATE', 20);
const duration = __ENV.DURATION || '60s';
const preAllocatedVUs = positiveInteger('PRE_ALLOCATED_VUS', 20);
const maxVUs = positiveInteger('MAX_VUS', 100);
const p95Milliseconds = positiveInteger('P95_MS', 500);
const summaryPath = __ENV.SUMMARY_PATH || 'load-test-summary.json';
const runId = (__ENV.RUN_ID || `local-${Date.now()}`)
  .replace(/[^A-Za-z0-9._:-]/g, '-')
  .slice(0, 40);

if (maxVUs < preAllocatedVUs) {
  throw new Error('MAX_VUS must be greater than or equal to PRE_ALLOCATED_VUS');
}

const successfulTransactions = new Rate('successful_transactions');

export const options = {
  scenarios: {
    transaction_ingestion: {
      executor: 'constant-arrival-rate',
      exec: 'ingestTransaction',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '15s',
      tags: {
        scenario_type: 'authenticated_transaction_ingestion',
      },
    },
  },
  thresholds: {
    'successful_transactions{operation:transaction_ingestion}': ['rate>0.99'],
    'http_req_failed{operation:transaction_ingestion}': ['rate<0.01'],
    'http_req_duration{operation:transaction_ingestion}': [`p(95)<${p95Milliseconds}`],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      tags: { operation: 'authentication' },
      timeout: '10s',
    },
  );

  const authenticated = check(response, {
    'local authentication succeeds': (result) => result.status === 200,
  });

  if (!authenticated) {
    fail(`Authentication failed with HTTP ${response.status}: ${response.body}`);
  }

  let responseBody;
  try {
    responseBody = response.json();
  } catch (error) {
    fail(`Authentication returned invalid JSON: ${error.message}`);
  }

  if (!responseBody.accessToken) {
    fail('Authentication response did not contain accessToken');
  }

  return { accessToken: responseBody.accessToken };
}

export function ingestTransaction(data) {
  const iteration = exec.scenario.iterationInTest;
  const uniqueId = `${runId}-${exec.vu.idInTest}-${iteration}`;
  const eventId = `load-event-${uniqueId}`;
  const transactionId = `load-txn-${uniqueId}`;
  const transactionTime = new Date().toISOString().slice(0, 19);

  const response = http.post(
    `${baseUrl}/api/v1/transaction-events`,
    JSON.stringify({
      eventId,
      transactionId,
      customerId: `load-customer-${uniqueId}`,
      amount: 25000.0,
      currency: 'ZAR',
      category: 'ELECTRONICS',
      transactionType: 'CARD_PURCHASE',
      merchant: 'Load Test Merchant',
      country: 'ZA',
      customerCountry: 'ZA',
      transactionTime,
    }),
    {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${data.accessToken}`,
        'Content-Type': 'application/json',
      },
      tags: { operation: 'transaction_ingestion' },
      timeout: '10s',
    },
  );

  let responseBody = {};
  try {
    responseBody = response.json();
  } catch (_) {
    // The checks below retain the response as a failed iteration without
    // stopping the remaining load profile.
  }

  const accepted = check(response, {
    'transaction is created': (result) => result.status === 201,
    'assessment is returned': () => Boolean(responseBody.assessmentId),
    'response matches submitted event': () => responseBody.eventId === eventId,
  });

  successfulTransactions.add(accepted, { operation: 'transaction_ingestion' });
}

function metricValue(data, metricName, valueName) {
  return data.metrics[metricName]?.values?.[valueName];
}

function formatNumber(value, digits = 2) {
  return typeof value === 'number' ? value.toFixed(digits) : 'n/a';
}

export function handleSummary(data) {
  const durationMetric = 'http_req_duration{operation:transaction_ingestion}';
  const failureMetric = 'http_req_failed{operation:transaction_ingestion}';
  const successMetric = 'successful_transactions{operation:transaction_ingestion}';
  const summary = [
    '',
    'Authenticated transaction-ingestion load test',
    `  target:              ${baseUrl}`,
    `  configured load:     ${rate} iterations/s for ${duration}`,
    `  completed iterations:${metricValue(data, 'iterations', 'count') ?? 'n/a'}`,
    `  dropped iterations:  ${metricValue(data, 'dropped_iterations', 'count') ?? 0}`,
    `  successful writes:   ${formatNumber((metricValue(data, successMetric, 'rate') || 0) * 100)}%`,
    `  HTTP failure rate:   ${formatNumber((metricValue(data, failureMetric, 'rate') || 0) * 100)}%`,
    `  latency avg:         ${formatNumber(metricValue(data, durationMetric, 'avg'))} ms`,
    `  latency p95:         ${formatNumber(metricValue(data, durationMetric, 'p(95)'))} ms`,
    `  latency p99:         ${formatNumber(metricValue(data, durationMetric, 'p(99)'))} ms`,
    `  latency max:         ${formatNumber(metricValue(data, durationMetric, 'max'))} ms`,
    `  machine summary:     ${summaryPath}`,
    '',
  ].join('\n');

  // k6 v2 includes the value returned by setup() in the summary object. That
  // value contains the short-lived bearer token, so never persist it as part
  // of the evidence artifact.
  const sanitizedData = { ...data };
  delete sanitizedData.setup_data;

  return {
    stdout: summary,
    [summaryPath]: JSON.stringify(sanitizedData, null, 2),
  };
}
