const { test, expect } = require('@playwright/test')
const {
  createUniqueTransactionTestData,
  localUsers,
  loginWithLocalCredentials,
  readStoredLocalAccessToken,
  submitTransactionForAssessment
} = require('./support')

test('operator can submit transactions but cannot read assessment history', async ({ page }, testInfo) => {
  await loginWithLocalCredentials(page, localUsers.operator)

  await expect(page.getByText(localUsers.operator.username, { exact: true })).toBeVisible()
  await expect(page.getByText('OPERATOR', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Run fraud assessment' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Assessment history is unavailable' })).toBeVisible()
  await expect(page.getByRole('form', { name: 'Assessment filters' })).toHaveCount(0)

  const accessToken = await readStoredLocalAccessToken(page)
  expect(accessToken).toBeTruthy()
  const forbiddenHistory = await page.request.get('/api/v1/fraud-assessments?page=0&size=1', {
    headers: { Authorization: `Bearer ${accessToken}` }
  })
  expect(forbiddenHistory.status()).toBe(403)

  const transactionData = createUniqueTransactionTestData(testInfo)
  await submitTransactionForAssessment(page, transactionData)
})
