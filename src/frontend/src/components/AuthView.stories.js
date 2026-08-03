import AuthView from './AuthView.vue'
import { expect, fn, userEvent, within } from '@storybook/test'

const localConfig = {
  provider: 'local',
  signupEnabled: false,
  local: {
    loginEndpoint: '/api/v1/auth/login'
  }
}

const cognitoConfig = {
  provider: 'cognito',
  signupEnabled: true,
  cognito: {
    domain: 'fraud-rule-engine.auth.af-south-1.amazoncognito.com',
    clientId: 'storybook-browser-client'
  }
}

export default {
  title: 'Authentication/AuthView',
  component: AuthView,
  parameters: {
    layout: 'fullscreen'
  },
  argTypes: {
    config: { control: false },
    onLogin: { action: 'login' },
    onSignup: { action: 'signup' },
    onLocalLogin: { action: 'local-login' }
  },
  args: {
    configured: true,
    busy: false,
    error: ''
  }
}

export const LocalSignIn = {
  args: {
    config: localConfig
  }
}

export const LocalCredentialsSubmission = {
  args: {
    config: localConfig,
    onLocalLogin: fn()
  },
  play: async ({ args, canvasElement }) => {
    const canvas = within(canvasElement)
    const password = canvas.getByLabelText('Password')

    await userEvent.type(canvas.getByLabelText('Username'), 'local-admin')
    await userEvent.type(password, 'local-admin-change-me')
    await userEvent.click(canvas.getByRole('button', { name: 'Sign in locally' }))

    await expect(args.onLocalLogin).toHaveBeenCalledWith({
      username: 'local-admin',
      password: 'local-admin-change-me'
    })
    await expect(password).toHaveValue('')
  }
}

export const LocalSignInBusy = {
  args: {
    config: localConfig,
    busy: true
  }
}

export const LocalAuthenticationError = {
  args: {
    config: localConfig,
    error: 'The username or password was not accepted.'
  }
}

export const LocalConfigurationMissing = {
  args: {
    config: localConfig,
    configured: false
  }
}

export const CognitoSignIn = {
  args: {
    config: {
      ...cognitoConfig,
      signupEnabled: false
    }
  }
}

export const CognitoWithSignUp = {
  args: {
    config: cognitoConfig
  }
}

export const CognitoConfigurationMissing = {
  args: {
    config: cognitoConfig,
    configured: false
  }
}
