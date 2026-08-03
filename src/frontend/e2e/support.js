const { expect } = require('@playwright/test')

const localUsers = {
  admin: {
    username: process.env.E2E_ADMIN_USERNAME || 'local-admin',
    password: process.env.E2E_ADMIN_PASSWORD || 'local-admin-change-me'
  },
  operator: {
    username: process.env.E2E_OPERATOR_USERNAME || 'local-operator',
    password: process.env.E2E_OPERATOR_PASSWORD || 'local-operator-change-me'
  }
}

async function loginWithLocalCredentials(page, userCredentials) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in to the console' })).toBeVisible()

  await page.getByLabel('Username', { exact: true }).fill(userCredentials.username)
  await page.getByLabel('Password', { exact: true }).fill(userCredentials.password)

  const loginResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' &&
    new URL(response.url()).pathname === '/api/v1/auth/login'
  )

  await page.getByRole('button', { name: 'Sign in locally' }).click()
  expect((await loginResponsePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: /Decisions you can/ })).toBeVisible()
}

function createUniqueTransactionTestData(testInfo) {
  const suffix = `${Date.now().toString(36)}-${testInfo.workerIndex}-${testInfo.retry}`
  return {
    eventId: `e2e-event-${suffix}`,
    transactionId: `e2e-transaction-${suffix}`,
    customerId: `e2e-customer-${suffix}`,
    amount: '25000.00',
    currency: 'ZAR',
    category: 'ELECTRONICS',
    transactionType: 'ONLINE_PURCHASE',
    merchant: 'Playwright Test Merchant',
    country: 'US',
    customerCountry: 'ZA',
    transactionTime: `${new Date().toISOString().slice(0, 10)}T02:15`
  }
}

async function fillTransactionForm(page, transactionData) {
  const transactionForm = page.locator('form.transaction-form')
  await expect(transactionForm).toBeVisible()

  await transactionForm.getByLabel(/Event ID/).fill(transactionData.eventId)
  await transactionForm.getByLabel(/Transaction ID/).fill(transactionData.transactionId)
  await transactionForm.getByLabel(/Customer ID/).fill(transactionData.customerId)
  await transactionForm.getByLabel(/Amount/).fill(transactionData.amount)
  await transactionForm.getByLabel(/Currency/).fill(transactionData.currency)
  await transactionForm.getByLabel(/Category/).fill(transactionData.category)
  await transactionForm.getByLabel(/Transaction type/).fill(transactionData.transactionType)
  await transactionForm.getByLabel(/Merchant \*/).fill(transactionData.merchant)
  await transactionForm.getByLabel(/Transaction time/).fill(transactionData.transactionTime)
  await transactionForm.getByLabel(/Merchant country/).fill(transactionData.country)
  await transactionForm.getByLabel(/Customer country/).fill(transactionData.customerCountry)
}

async function submitTransactionForAssessment(page, transactionData) {
  await fillTransactionForm(page, transactionData)

  const assessmentResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' &&
    new URL(response.url()).pathname === '/api/v1/transaction-events'
  )

  await page.getByRole('button', { name: 'Run fraud assessment' }).click()
  const assessmentResponse = await assessmentResponsePromise
  expect(assessmentResponse.status()).toBe(201)

  const assessment = await assessmentResponse.json()
  expect(assessment).toMatchObject({
    eventId: transactionData.eventId,
    transactionId: transactionData.transactionId,
    customerId: transactionData.customerId,
    flagged: true
  })

  await expect(page.getByRole('heading', { name: transactionData.transactionId })).toBeVisible()
  await expect(page.getByText('Flagged for review', { exact: true })).toBeVisible()
  return assessment
}

async function readStoredLocalAccessToken(page) {
  return page.evaluate(() => {
    const serialized = window.sessionStorage.getItem('fraud-rule-engine.local.tokens')
    return serialized ? JSON.parse(serialized).accessToken : null
  })
}

module.exports = {
  createUniqueTransactionTestData,
  fillTransactionForm,
  localUsers,
  loginWithLocalCredentials,
  readStoredLocalAccessToken,
  submitTransactionForAssessment
}
