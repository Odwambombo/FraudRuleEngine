<template>
  <main class="auth-shell">
    <section class="auth-story" aria-labelledby="auth-title">
      <a class="auth-brand" href="/" aria-label="Fraud Rule Engine home">
        <span aria-hidden="true">FR</span>
        <strong>Fraud Rule Engine</strong>
      </a>

      <div class="story-copy">
        <p class="eyebrow">Secure decision workspace</p>
        <h1 id="auth-title">See the signal.<br>Explain the decision.</h1>
        <p>
          Submit transaction events, review risk scores, and trace every matched rule from one
          focused operations console.
        </p>
      </div>

      <div class="signal-preview" aria-hidden="true">
        <div class="signal-topline">
          <span>Live decision</span>
          <i></i>
        </div>
        <div class="signal-score">
          <strong>82</strong>
          <div>
            <span>Critical risk</span>
            <small>3 rules matched</small>
          </div>
        </div>
        <div class="signal-bars">
          <i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i>
        </div>
      </div>

      <p class="story-footnote">Built for fraud operators, analysts, auditors, and administrators.</p>
    </section>

    <section class="auth-panel" aria-labelledby="sign-in-title">
      <div class="auth-card">
        <div class="lock-mark" aria-hidden="true">
          <span></span>
        </div>
        <p class="eyebrow">Protected access</p>
        <h2 id="sign-in-title">Sign in to the console</h2>
        <p v-if="usesCognitoAuthentication" class="auth-intro">
          Continue to your organization’s Amazon Cognito login. Credentials are entered only on
          the identity provider’s hosted page.
        </p>
        <p v-else class="auth-intro">
          Use the local development account configured by the application. Your password is sent
          only to this same-origin service and is never stored in the browser.
        </p>

        <div v-if="error" class="auth-alert" role="alert">
          <strong>Sign-in was not completed</strong>
          <p>{{ error }}</p>
        </div>

        <div v-if="!configured" class="auth-alert configuration-alert" role="alert">
          <strong>Login is not configured</strong>
          <p v-if="usesCognitoAuthentication">
            Set the public Cognito domain and browser client ID in the application environment,
            then reload this page.
          </p>
          <p v-else>Enable the local login endpoint in the application, then reload this page.</p>
        </div>

        <form v-if="usesLocalAuthentication" class="local-login-form" @submit.prevent="submitLocalCredentials">
          <div class="form-field">
            <label for="local-username">Username</label>
            <input
              id="local-username"
              v-model.trim="username"
              name="username"
              type="text"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              :disabled="busy || !configured"
              required
            >
          </div>

          <div class="form-field">
            <label for="local-password">Password</label>
            <input
              id="local-password"
              v-model="password"
              name="password"
              type="password"
              autocomplete="current-password"
              :disabled="busy || !configured"
              required
            >
          </div>

          <button class="primary-button" type="submit" :disabled="busy || !configured">
            <span>{{ busy ? 'Signing in…' : 'Sign in locally' }}</span>
            <b aria-hidden="true">→</b>
          </button>
        </form>

        <div v-else class="auth-actions">
          <button class="primary-button" type="button" :disabled="busy || !configured" @click="$emit('login')">
            <span>{{ busy ? 'Preparing secure sign-in…' : 'Continue with Cognito' }}</span>
            <b aria-hidden="true">→</b>
          </button>
          <button
            v-if="config.signupEnabled"
            class="secondary-button"
            type="button"
            :disabled="busy || !configured"
            @click="$emit('signup')"
          >
            Create an account
          </button>
        </div>

        <div class="security-note">
          <span aria-hidden="true">✓</span>
          <p v-if="usesCognitoAuthentication">Authorization code flow with PKCE. No client secret or password is stored here.</p>
          <p v-else>The JWT is kept in session storage and is removed when you sign out or close this tab.</p>
        </div>

        <p class="authority-note">
          Screen controls improve the experience; the backend makes the final authorization
          decision for every request.
        </p>
      </div>
    </section>
  </main>
</template>

<script>
export default {
  name: 'AuthView',
  emits: ['login', 'signup', 'local-login'],
  props: {
    config: {
      type: Object,
      required: true
    },
    configured: {
      type: Boolean,
      required: true
    },
    busy: {
      type: Boolean,
      default: false
    },
    error: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      username: '',
      password: ''
    }
  },
  computed: {
    usesLocalAuthentication() {
      return String(this.config.provider || '').toLowerCase() === 'local'
    },
    usesCognitoAuthentication() {
      return !this.usesLocalAuthentication
    }
  },
  methods: {
    submitLocalCredentials() {
      if (this.busy || !this.configured) return
      this.$emit('local-login', {
        username: this.username,
        password: this.password
      })
      this.password = ''
    }
  }
}
</script>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(430px, 0.92fr);
  min-height: 100vh;
  color: #e8eef6;
  background: #0d1728;
}

.auth-story,
.auth-panel {
  position: relative;
  min-width: 0;
}

.auth-story {
  display: flex;
  overflow: hidden;
  flex-direction: column;
  justify-content: space-between;
  padding: 42px clamp(32px, 6vw, 88px) 36px;
  background:
    radial-gradient(circle at 78% 26%, rgba(38, 132, 129, 0.2), transparent 28%),
    radial-gradient(circle at 22% 80%, rgba(181, 68, 70, 0.18), transparent 32%),
    linear-gradient(145deg, #101d31, #0b1423 70%);
}

.auth-story::after {
  position: absolute;
  width: 540px;
  height: 540px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 50%;
  content: '';
  right: -290px;
  top: 20%;
}

.auth-brand {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 11px;
  color: #fff;
  text-decoration: none;
}

.auth-brand > span {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.19);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.07);
  font-size: 0.7rem;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.auth-brand strong {
  font-size: 0.92rem;
  letter-spacing: -0.01em;
}

.story-copy {
  position: relative;
  z-index: 1;
  max-width: 650px;
  margin: 80px 0 36px;
}

.eyebrow {
  margin: 0 0 13px;
  color: #6ed0c6;
  font-size: 0.69rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.story-copy h1 {
  margin: 0 0 24px;
  color: #f8fbff;
  font-size: clamp(3.25rem, 6vw, 6.1rem);
  font-weight: 720;
  letter-spacing: -0.067em;
  line-height: 0.92;
}

.story-copy > p:last-child {
  max-width: 560px;
  margin: 0;
  color: #aab7c9;
  font-size: 1.02rem;
  line-height: 1.7;
}

.signal-preview {
  position: relative;
  z-index: 1;
  width: min(480px, 100%);
  padding: 19px 20px 17px;
  border: 1px solid rgba(151, 170, 193, 0.17);
  border-radius: 18px;
  background: rgba(15, 28, 47, 0.78);
  box-shadow: 0 22px 70px rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(14px);
}

.signal-topline,
.signal-score {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.signal-topline {
  color: #8392a7;
  font-size: 0.65rem;
  font-weight: 800;
  text-transform: uppercase;
}

.signal-topline i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #47b789;
  box-shadow: 0 0 0 5px rgba(71, 183, 137, 0.12);
}

.signal-score {
  justify-content: flex-start;
  gap: 18px;
  margin: 14px 0 16px;
}

.signal-score > strong {
  color: #ff8d82;
  font-size: 2.45rem;
  line-height: 1;
}

.signal-score div {
  display: grid;
  gap: 3px;
}

.signal-score span {
  color: #f2f6fb;
  font-size: 0.82rem;
  font-weight: 800;
}

.signal-score small {
  color: #8493a8;
  font-size: 0.68rem;
}

.signal-bars {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 5px;
  align-items: end;
  height: 32px;
}

.signal-bars i {
  height: 24%;
  border-radius: 3px 3px 1px 1px;
  background: #2c746f;
}

.signal-bars i:nth-child(2) { height: 45%; }
.signal-bars i:nth-child(3) { height: 32%; }
.signal-bars i:nth-child(4) { height: 68%; }
.signal-bars i:nth-child(5) { height: 50%; background: #b56b35; }
.signal-bars i:nth-child(6) { height: 82%; background: #b54b50; }
.signal-bars i:nth-child(7) { height: 64%; background: #b54b50; }
.signal-bars i:nth-child(8) { height: 100%; background: #d15c62; }

.story-footnote {
  position: relative;
  z-index: 1;
  margin: 28px 0 0;
  color: #6f7d92;
  font-size: 0.72rem;
}

.auth-panel {
  display: grid;
  place-items: center;
  padding: 48px clamp(28px, 6vw, 80px);
  color: #172033;
  background: #f4f6f8;
}

.auth-card {
  width: min(440px, 100%);
}

.auth-card .eyebrow {
  color: #24766d;
}

.lock-mark {
  position: relative;
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 28px;
  place-items: center;
  border-radius: 15px;
  background: #152942;
  box-shadow: 0 11px 26px rgba(21, 41, 66, 0.18);
}

.lock-mark span {
  width: 16px;
  height: 13px;
  margin-top: 7px;
  border-radius: 3px;
  background: #fff;
}

.lock-mark span::before {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid #fff;
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
  content: '';
  left: 18px;
  top: 10px;
}

.auth-card h2 {
  margin: 0 0 12px;
  color: #142033;
  font-size: clamp(2rem, 4vw, 2.7rem);
  letter-spacing: -0.05em;
  line-height: 1.05;
}

.auth-intro {
  margin: 0 0 27px;
  color: #657286;
  line-height: 1.65;
}

.auth-alert {
  margin-bottom: 17px;
  padding: 13px 14px;
  border: 1px solid #efc9cb;
  border-radius: 11px;
  color: #8f2933;
  background: #fff5f5;
}

.auth-alert strong {
  display: block;
  margin-bottom: 3px;
  font-size: 0.78rem;
}

.auth-alert p {
  margin: 0;
  font-size: 0.76rem;
  line-height: 1.5;
}

.configuration-alert {
  border-color: #e8d19b;
  color: #7a551b;
  background: #fffaf0;
}

.auth-actions {
  display: grid;
  gap: 10px;
}

.local-login-form {
  display: grid;
  gap: 16px;
}

.form-field {
  display: grid;
  gap: 7px;
}

.form-field label {
  color: #29384d;
  font-size: 0.74rem;
  font-weight: 850;
}

.form-field input {
  width: 100%;
  min-height: 49px;
  padding: 0 13px;
  border: 1px solid #c7d0db;
  border-radius: 10px;
  color: #16253a;
  background: #fff;
  outline: 0;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}

.form-field input:hover:not(:disabled) {
  border-color: #8e9caf;
}

.form-field input:focus-visible {
  border-color: #277d75;
  box-shadow: 0 0 0 3px rgba(39, 125, 117, 0.16);
}

.form-field input:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.primary-button,
.secondary-button {
  min-height: 52px;
  border-radius: 10px;
  cursor: pointer;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 850;
}

.primary-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border: 0;
  color: #fff;
  background: #17314e;
  box-shadow: 0 12px 26px rgba(23, 49, 78, 0.17);
}

.local-login-form .primary-button {
  width: 100%;
  margin-top: 2px;
}

.primary-button:hover:not(:disabled),
.primary-button:focus-visible:not(:disabled) {
  background: #225678;
}

.primary-button b {
  font-size: 1.1rem;
}

.secondary-button {
  border: 1px solid #cbd3dd;
  color: #26354b;
  background: #fff;
}

.secondary-button:hover:not(:disabled),
.secondary-button:focus-visible:not(:disabled) {
  border-color: #7790a9;
  background: #f9fbfd;
}

button:focus-visible {
  outline: 3px solid rgba(37, 132, 125, 0.34);
  outline-offset: 2px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.security-note {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 20px;
  padding: 13px;
  border: 1px solid #dce4eb;
  border-radius: 10px;
  background: #f9fbfc;
}

.security-note span {
  display: grid;
  width: 19px;
  height: 19px;
  flex: 0 0 19px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #27836a;
  font-size: 0.62rem;
  font-weight: 900;
}

.security-note p,
.authority-note {
  color: #5f6b7d;
  font-size: 0.7rem;
  line-height: 1.5;
}

.security-note p {
  margin: 0;
}

.authority-note {
  margin: 18px 0 0;
  text-align: center;
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-story {
    min-height: 510px;
  }

  .story-copy {
    margin-top: 64px;
  }

  .auth-panel {
    min-height: 620px;
  }
}

@media (max-width: 560px) {
  .auth-story {
    min-height: 460px;
    padding: 26px 22px 24px;
  }

  .story-copy {
    margin: 55px 0 24px;
  }

  .story-copy h1 {
    font-size: 3.2rem;
  }

  .signal-preview {
    display: none;
  }

  .auth-panel {
    min-height: 570px;
    padding: 42px 22px;
  }
}
</style>
