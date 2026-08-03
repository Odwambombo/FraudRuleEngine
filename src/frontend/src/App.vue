<template>
  <div class="application-root">
    <main v-if="phase === 'loading'" class="system-state" aria-live="polite">
      <div class="state-brand" aria-hidden="true">FR</div>
      <span class="state-spinner" aria-hidden="true"></span>
      <h1>Preparing the decision console</h1>
      <p>Loading runtime security and permission configuration…</p>
    </main>

    <main v-else-if="phase === 'error'" class="system-state error-state" role="alert">
      <div class="state-brand error-mark" aria-hidden="true">!</div>
      <p class="state-kicker">Startup failed</p>
      <h1>The application API is unavailable</h1>
      <p>{{ fatalError }}</p>
      <button type="button" @click="initializeApplication">Try again</button>
    </main>

    <AuthView
      v-else-if="!isApplicationAccessGranted"
      :config="runtimeConfig"
      :configured="isLoginConfigured"
      :busy="authBusy"
      :error="authError"
      @login="startLogin"
      @signup="startSignup"
      @local-login="authenticateWithLocalCredentials"
    />

    <DashboardView
      v-else
      :api-client="apiClient"
      :config="runtimeConfig"
      :session="session"
      :authentication-disabled="authenticationDisabled"
      :permission-evaluations="permissionEvaluations"
      @logout="signOut"
      @session-expired="handleExpiredSession"
    />
  </div>
</template>

<script>
import AuthView from './components/AuthView.vue'
import DashboardView from './components/DashboardView.vue'
import { CognitoPkceClient, LocalJwtClient, evaluatePermission } from './services/auth'
import { createApiClient, loadFrontendConfig } from './services/api'

export default {
  name: 'App',
  components: {
    AuthView,
    DashboardView
  },
  data() {
    return {
      phase: 'loading',
      runtimeConfig: null,
      authClient: null,
      apiClient: null,
      session: null,
      authBusy: false,
      authError: '',
      fatalError: ''
    }
  },
  computed: {
    authenticationDisabled() {
      return this.runtimeConfig?.authenticationRequired === false
    },
    authenticationProvider() {
      return String(this.runtimeConfig?.provider || '').toLowerCase()
    },
    isLoginConfigured() {
      return Boolean(
        this.runtimeConfig?.loginConfigured &&
        ['cognito', 'local'].includes(this.authenticationProvider) &&
        this.authClient
      )
    },
    isApplicationAccessGranted() {
      return this.authenticationDisabled || Boolean(this.session)
    },
    permissionEvaluations() {
      const permissionConfig = this.runtimeConfig?.permissions || {}
      return {
        transactionWrite: evaluatePermission(
          permissionConfig.transactionWrite,
          this.session,
          this.authenticationDisabled
        ),
        assessmentRead: evaluatePermission(
          permissionConfig.assessmentRead,
          this.session,
          this.authenticationDisabled
        )
      }
    }
  },
  mounted() {
    this.initializeApplication()
  },
  methods: {
    async initializeApplication() {
      this.phase = 'loading'
      this.fatalError = ''
      this.authError = ''
      this.session = null
      this.authClient = null
      this.apiClient = null

      try {
        this.runtimeConfig = await loadFrontendConfig()

        if (this.runtimeConfig.authenticationRequired) {
          if (this.runtimeConfig.loginConfigured) {
            try {
              if (this.authenticationProvider === 'cognito') {
                this.authClient = new CognitoPkceClient(this.runtimeConfig)
              } else if (this.authenticationProvider === 'local') {
                this.authClient = new LocalJwtClient(this.runtimeConfig)
              }

              if (this.authenticationProvider === 'cognito' &&
                  this.authClient.hasOauthAuthorizationResponse()) {
                this.session = await this.authClient.handleOauthAuthorizationResponse()
              } else {
                this.session = await this.authClient.restoreSession()
              }
            } catch (error) {
              this.authClient?.clearSession()
              this.session = null
              this.authError = error.message || 'Sign-in could not be completed.'
            }
          }
        }

        this.apiClient = createApiClient({
          authenticationRequired: this.runtimeConfig.authenticationRequired,
          authClient: this.authClient,
          onSessionChanged: this.updateAuthenticatedSession
        })
        this.phase = 'ready'
      } catch (error) {
        this.fatalError = error.message || 'Runtime configuration could not be loaded.'
        this.phase = 'error'
      }
    },
    async startCognitoAuthorization(authorizationMode) {
      if (!this.authClient || this.authBusy) return
      this.authBusy = true
      this.authError = ''
      try {
        await this.authClient.startOauthAuthorization(authorizationMode)
      } catch (error) {
        this.authError = error.message || 'Secure sign-in could not be started.'
        this.authBusy = false
      }
    },
    startLogin() {
      this.startCognitoAuthorization('login')
    },
    startSignup() {
      this.startCognitoAuthorization('signup')
    },
    async authenticateWithLocalCredentials(loginCredentials) {
      if (!(this.authClient instanceof LocalJwtClient) || this.authBusy) return
      this.authBusy = true
      this.authError = ''
      try {
        this.session = await this.authClient.login(loginCredentials.username, loginCredentials.password)
      } catch (error) {
        this.authClient.clearSession()
        this.session = null
        this.authError = error.message || 'Local sign-in could not be completed.'
      } finally {
        this.authBusy = false
      }
    },
    updateAuthenticatedSession(updatedSession) {
      this.session = updatedSession
      if (!updatedSession && this.runtimeConfig?.authenticationRequired) {
        this.authError = 'Your session expired or was rejected. Sign in again to continue.'
      }
    },
    handleExpiredSession() {
      this.authClient?.clearSession()
      this.session = null
      this.authError = 'Your session is no longer authenticated. Sign in again to continue.'
    },
    signOut() {
      if (!this.authClient) return
      this.authClient.logout()
      if (this.authenticationProvider === 'local') {
        this.session = null
        this.authError = ''
      }
    }
  }
}
</script>

<style>
:root {
  color-scheme: light;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
}

* {
  box-sizing: border-box;
}

html {
  min-width: 320px;
  scroll-behavior: smooth;
}

body {
  min-width: 320px;
  min-height: 100vh;
  margin: 0;
  background: #f3f5f7;
}

button,
input,
select {
  font: inherit;
}

#app,
.application-root {
  min-height: 100vh;
}

.system-state {
  display: grid;
  min-height: 100vh;
  place-content: center;
  justify-items: center;
  padding: 30px;
  color: #dce8f2;
  background:
    radial-gradient(circle at 50% 28%, rgba(46, 116, 111, 0.24), transparent 26%),
    #0e192a;
  text-align: center;
}

.state-brand {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 24px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 14px;
  color: #fff;
  background: rgba(255, 255, 255, 0.07);
  font-size: 0.74rem;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.state-spinner {
  width: 28px;
  height: 28px;
  margin-bottom: 18px;
  border: 2px solid rgba(196, 214, 228, 0.25);
  border-top-color: #66c6ba;
  border-radius: 50%;
  animation: application-spin 0.8s linear infinite;
}

.system-state h1 {
  max-width: 650px;
  margin: 0 0 8px;
  color: #f5f9fc;
  font-size: clamp(1.7rem, 4vw, 2.6rem);
  letter-spacing: -0.04em;
}

.system-state > p:last-of-type {
  max-width: 560px;
  margin: 0;
  color: #93a3b7;
  line-height: 1.6;
}

.system-state button {
  min-height: 43px;
  margin-top: 22px;
  padding: 0 16px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 9px;
  color: #fff;
  background: #245c6e;
  cursor: pointer;
  font-weight: 800;
}

.error-state {
  background:
    radial-gradient(circle at 50% 28%, rgba(165, 50, 59, 0.22), transparent 26%),
    #171724;
}

.error-mark {
  color: #ffd9dc;
  background: rgba(177, 52, 62, 0.22);
}

.state-kicker {
  margin: 0 0 9px;
  color: #ef9aa1;
  font-size: 0.68rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

@keyframes application-spin {
  to { transform: rotate(360deg); }
}
</style>
