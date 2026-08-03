const { test, expect } = require('@playwright/test')
const {
  createUniqueTransactionTestData,
  localUsers,
  loginWithLocalCredentials,
  submitTransactionForAssessment
} = require('./support')

test('administrator submits a transaction and retrieves it from history', async ({ page }, testInfo) => {
  await loginWithLocalCredentials(page, localUsers.admin)

  const permissionSnapshot = page.getByRole('complementary', { name: 'Permission snapshot' })
  await expect(permissionSnapshot.getByText('Permission and role present')).toHaveCount(2)

  const transactionData = createUniqueTransactionTestData(testInfo)
  const assessment = await submitTransactionForAssessment(page, transactionData)

  const assessmentFilters = page.getByRole('form', { name: 'Assessment filters' })
  await assessmentFilters.getByLabel('Customer ID', { exact: true }).fill(transactionData.customerId)

  const historyResponsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return response.request().method() === 'GET' &&
      url.pathname === '/api/v1/fraud-assessments' &&
      url.searchParams.get('customerId') === transactionData.customerId
  })

  await assessmentFilters.getByRole('button', { name: 'Apply filters' }).click()
  expect((await historyResponsePromise).status()).toBe(200)

  const assessmentRow = page.getByRole('row').filter({ hasText: transactionData.transactionId })
  await expect(assessmentRow).toContainText(transactionData.customerId)
  await expect(assessmentRow).toContainText('Flagged')

  const detailResponsePromise = page.waitForResponse((response) =>
    response.request().method() === 'GET' &&
    new URL(response.url()).pathname === `/api/v1/fraud-assessments/${assessment.assessmentId}`
  )

  await assessmentRow.getByRole('button', { name: 'Inspect' }).click()
  expect((await detailResponsePromise).status()).toBe(200)
  await expect(page.getByRole('heading', { name: transactionData.transactionId })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Matched rules' })).toBeVisible()
})
