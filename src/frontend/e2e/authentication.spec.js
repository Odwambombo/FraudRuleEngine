const { test, expect } = require('@playwright/test')
const { localUsers, loginWithLocalCredentials } = require('./support')

test.describe('local authentication', () => {
  test('rejects invalid credentials without creating a browser session', async ({ page }) => {
    await page.goto('/')

    await page.getByLabel('Username', { exact: true }).fill(localUsers.admin.username)
    await page.getByLabel('Password', { exact: true }).fill('not-the-password')

    const loginResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/v1/auth/login'
    )

    await page.getByRole('button', { name: 'Sign in locally' }).click()
    expect((await loginResponsePromise).status()).toBe(401)

    await expect(page.getByRole('alert')).toContainText('The username or password is incorrect.')
    await expect(page.getByRole('heading', { name: 'Sign in to the console' })).toBeVisible()
    await expect.poll(() => page.evaluate(() =>
      window.sessionStorage.getItem('fraud-rule-engine.local.tokens')
    )).toBeNull()
  })

  test('signs an administrator in and removes the session on sign-out', async ({ page }) => {
    await loginWithLocalCredentials(page, localUsers.admin)

    await expect(page.getByText(localUsers.admin.username, { exact: true })).toBeVisible()
    await expect(page.getByText('ADMIN', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page.getByRole('heading', { name: 'Sign in to the console' })).toBeVisible()
    await expect.poll(() => page.evaluate(() =>
      window.sessionStorage.getItem('fraud-rule-engine.local.tokens')
    )).toBeNull()
  })
})
