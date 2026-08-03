<template>
  <main class="dashboard-shell">
    <header class="topbar">
      <a class="brand" href="#overview" aria-label="Fraud Rule Engine dashboard">
        <span aria-hidden="true">FR</span>
        <div>
          <strong>Fraud Rule Engine</strong>
          <small>Decision console</small>
        </div>
      </a>

      <nav aria-label="Primary navigation">
        <a href="#overview">Overview</a>
        <a href="#new-assessment">Assess</a>
        <a href="#assessment-history">History</a>
      </nav>

      <div class="account-area">
        <span v-if="authenticationDisabled" class="environment-badge local">Local mode</span>
        <span v-else class="environment-badge">Cognito</span>
        <div class="identity">
          <span aria-hidden="true">{{ initials }}</span>
          <div>
            <strong>{{ currentUserProfile.displayName }}</strong>
            <small>{{ identitySubtitle }}</small>
          </div>
        </div>
        <button v-if="!authenticationDisabled" class="logout-button" type="button" @click="$emit('logout')">
          Sign out
        </button>
      </div>
    </header>

    <div v-if="authenticationDisabled" class="local-banner" role="status">
      <span aria-hidden="true">i</span>
      <p>
        Authentication is disabled for this environment. Requests are sent without a bearer token.
      </p>
    </div>

    <section id="overview" class="hero-section" aria-labelledby="dashboard-title">
      <div class="hero-copy">
        <p class="eyebrow">Risk operations</p>
        <h1 id="dashboard-title">Decisions you can<br>trace and defend.</h1>
        <p>
          Evaluate new transaction events, inspect matched rules, and review the latest fraud
          assessments without losing the evidence behind each score.
        </p>
        <div class="hero-actions">
          <a class="primary-link" href="#new-assessment">Assess a transaction</a>
          <a class="secondary-link" href="#assessment-history">Review history</a>
        </div>
      </div>

      <aside class="access-card" aria-labelledby="access-title">
        <div class="access-card-heading">
          <div>
            <p class="eyebrow">Session access</p>
            <h2 id="access-title">Permission snapshot</h2>
          </div>
          <span class="connection-dot" role="img" aria-label="Application ready"></span>
        </div>
        <div class="access-list">
          <div>
            <span class="access-icon" :class="permissionEvaluations.transactionWrite.allowed ? 'granted' : 'denied'">
              {{ permissionEvaluations.transactionWrite.allowed ? '✓' : '—' }}
            </span>
            <p>
              <strong>Submit transactions</strong>
              <small>{{ permissionStatusSummary(permissionEvaluations.transactionWrite) }}</small>
            </p>
          </div>
          <div>
            <span class="access-icon" :class="permissionEvaluations.assessmentRead.allowed ? 'granted' : 'denied'">
              {{ permissionEvaluations.assessmentRead.allowed ? '✓' : '—' }}
            </span>
            <p>
              <strong>Read assessments</strong>
              <small>{{ permissionStatusSummary(permissionEvaluations.assessmentRead) }}</small>
            </p>
          </div>
        </div>
        <p class="backend-authority">
          These checks shape the interface only. The backend makes the final authorization
          decision for every request.
        </p>
      </aside>
    </section>

    <section class="metric-grid" aria-label="Loaded assessment summary">
      <article>
        <span class="metric-icon navy" aria-hidden="true">Σ</span>
        <div>
          <small>Total assessments</small>
          <strong>{{ assessmentMetrics.total }}</strong>
          <p>Matching the current filters</p>
        </div>
      </article>
      <article>
        <span class="metric-icon red" aria-hidden="true">!</span>
        <div>
          <small>High risk loaded</small>
          <strong>{{ assessmentMetrics.highRisk }}</strong>
          <p>High and critical on this page</p>
        </div>
      </article>
      <article>
        <span class="metric-icon amber" aria-hidden="true">⚑</span>
        <div>
          <small>Flagged loaded</small>
          <strong>{{ assessmentMetrics.flagged }}</strong>
          <p>Transactions requiring attention</p>
        </div>
      </article>
      <article>
        <span class="metric-icon teal" aria-hidden="true">Ø</span>
        <div>
          <small>Average loaded score</small>
          <strong>{{ assessmentMetrics.averageScore }}</strong>
          <p>Across the current result page</p>
        </div>
      </article>
    </section>

    <section id="new-assessment" class="workspace-section" aria-labelledby="assessment-form-title">
      <div class="section-intro">
        <div>
          <p class="eyebrow">New decision</p>
          <h2 id="assessment-form-title">Assess a transaction</h2>
        </div>
        <p>
          Send a complete event to the rule engine. Reusing an event ID with the same payload is
          idempotent; changing its payload produces a conflict.
        </p>
      </div>

      <div class="workspace-grid">
        <article class="form-card">
          <div class="card-heading">
            <div>
              <span class="step-number">1</span>
              <div>
                <strong>Transaction event</strong>
                <small>Required fields are marked with an asterisk</small>
              </div>
            </div>
            <button
              v-if="permissionEvaluations.transactionWrite.allowed"
              class="text-button"
              type="button"
              :disabled="submitting"
              @click="resetTransactionForm"
            >
              Reset
            </button>
          </div>

          <div v-if="!permissionEvaluations.transactionWrite.allowed" class="permission-panel">
            <span aria-hidden="true">🔒</span>
            <h3>Transaction submission is unavailable</h3>
            <p>{{ describePermissionRequirement(permissionEvaluations.transactionWrite) }}</p>
            <small>The backend will independently evaluate authorization if a request is made.</small>
          </div>

          <form v-else class="transaction-form" @submit.prevent="submitTransactionForAssessment">
            <fieldset>
              <legend>Event identity</legend>
              <div class="field-grid three-columns">
                <label>
                  <span>Event ID *</span>
                  <input
                    v-model.trim="transaction.eventId"
                    name="eventId"
                    maxlength="100"
                    pattern="[A-Za-z0-9][A-Za-z0-9._:-]*"
                    required
                    autocomplete="off"
                  >
                  <small v-if="fieldValidationError('eventId')" class="field-error">{{ fieldValidationError('eventId') }}</small>
                </label>
                <label>
                  <span>Transaction ID *</span>
                  <input
                    v-model.trim="transaction.transactionId"
                    name="transactionId"
                    maxlength="100"
                    pattern="[A-Za-z0-9][A-Za-z0-9._:-]*"
                    required
                    autocomplete="off"
                  >
                  <small v-if="fieldValidationError('transactionId')" class="field-error">{{ fieldValidationError('transactionId') }}</small>
                </label>
                <label>
                  <span>Customer ID *</span>
                  <input
                    v-model.trim="transaction.customerId"
                    name="customerId"
                    maxlength="100"
                    pattern="[A-Za-z0-9][A-Za-z0-9._:-]*"
                    required
                    autocomplete="off"
                  >
                  <small v-if="fieldValidationError('customerId')" class="field-error">{{ fieldValidationError('customerId') }}</small>
                </label>
              </div>
            </fieldset>

            <fieldset>
              <legend>Transaction details</legend>
              <div class="field-grid">
                <label>
                  <span>Amount *</span>
                  <input
                    v-model="transaction.amount"
                    name="amount"
                    type="number"
                    min="0.01"
                    max="99999999999999999.99"
                    step="0.01"
                    inputmode="decimal"
                    required
                  >
                  <small v-if="fieldValidationError('amount')" class="field-error">{{ fieldValidationError('amount') }}</small>
                </label>
                <label>
                  <span>Currency *</span>
                  <input
                    v-model.trim="transaction.currency"
                    name="currency"
                    maxlength="3"
                    pattern="[A-Za-z]{3}"
                    placeholder="ZAR"
                    required
                    autocomplete="off"
                  >
                  <small v-if="fieldValidationError('currency')" class="field-error">{{ fieldValidationError('currency') }}</small>
                </label>
                <label>
                  <span>Category *</span>
                  <input
                    v-model.trim="transaction.category"
                    name="category"
                    maxlength="64"
                    list="category-options"
                    required
                    autocomplete="off"
                  >
                  <small v-if="fieldValidationError('category')" class="field-error">{{ fieldValidationError('category') }}</small>
                </label>
                <label>
                  <span>Transaction type *</span>
                  <input
                    v-model.trim="transaction.transactionType"
                    name="transactionType"
                    maxlength="64"
                    list="transaction-type-options"
                    required
                    autocomplete="off"
                  >
                  <small v-if="fieldValidationError('transactionType')" class="field-error">{{ fieldValidationError('transactionType') }}</small>
                </label>
                <label class="wide-field">
                  <span>Merchant *</span>
                  <input
                    v-model.trim="transaction.merchant"
                    name="merchant"
                    maxlength="255"
                    required
                    autocomplete="organization"
                  >
                  <small v-if="fieldValidationError('merchant')" class="field-error">{{ fieldValidationError('merchant') }}</small>
                </label>
                <label class="wide-field">
                  <span>Transaction time *</span>
                  <input
                    v-model="transaction.transactionTime"
                    name="transactionTime"
                    type="datetime-local"
                    required
                  >
                  <small v-if="fieldValidationError('transactionTime')" class="field-error">{{ fieldValidationError('transactionTime') }}</small>
                </label>
              </div>
            </fieldset>

            <fieldset>
              <legend>Location context</legend>
              <div class="field-grid">
                <label>
                  <span>Merchant country *</span>
                  <input
                    v-model.trim="transaction.country"
                    name="country"
                    maxlength="2"
                    pattern="[A-Za-z]{2}"
                    placeholder="ZA"
                    required
                    autocomplete="country"
                  >
                  <small v-if="fieldValidationError('country')" class="field-error">{{ fieldValidationError('country') }}</small>
                </label>
                <label>
                  <span>Customer country</span>
                  <input
                    v-model.trim="transaction.customerCountry"
                    name="customerCountry"
                    maxlength="2"
                    pattern="[A-Za-z]{2}"
                    placeholder="ZA"
                    autocomplete="country"
                  >
                  <small v-if="fieldValidationError('customerCountry')" class="field-error">{{ fieldValidationError('customerCountry') }}</small>
                </label>
              </div>
            </fieldset>

            <datalist id="category-options">
              <option value="GROCERIES"></option>
              <option value="ELECTRONICS"></option>
              <option value="FUEL"></option>
              <option value="GAMBLING"></option>
              <option value="CRYPTOCURRENCY"></option>
            </datalist>
            <datalist id="transaction-type-options">
              <option value="CARD_PURCHASE"></option>
              <option value="TRANSFER"></option>
              <option value="CASH_WITHDRAWAL"></option>
              <option value="ONLINE_PURCHASE"></option>
            </datalist>

            <div v-if="submitError" class="request-error" :class="requestErrorCssClass(submitError)" role="alert">
              <div>
                <strong>{{ requestErrorTitle(submitError) }}</strong>
                <p>{{ submitError.message }}</p>
              </div>
              <button v-if="submitError.status === 401" type="button" @click="$emit('session-expired')">
                Sign in again
              </button>
            </div>

            <button class="submit-button" type="submit" :disabled="submitting">
              <span>{{ submitting ? 'Evaluating transaction…' : 'Run fraud assessment' }}</span>
              <b aria-hidden="true">{{ submitting ? '•••' : '→' }}</b>
            </button>
          </form>
        </article>

        <div class="result-column">
          <div v-if="resultLoading" class="result-loading" role="status">
            <span aria-hidden="true"></span>
            Loading assessment evidence…
          </div>
          <div v-if="resultError" class="request-error result-request-error" :class="requestErrorCssClass(resultError)" role="alert">
            <div>
              <strong>{{ requestErrorTitle(resultError) }}</strong>
              <p>{{ resultError.message }}</p>
            </div>
            <button v-if="resultError.status === 401" type="button" @click="$emit('session-expired')">
              Sign in again
            </button>
          </div>
          <AssessmentResult :assessment="currentAssessment" />
        </div>
      </div>
    </section>

    <section id="assessment-history" class="history-section" aria-labelledby="history-title">
      <div class="section-intro history-intro">
        <div>
          <p class="eyebrow">Decision history</p>
          <h2 id="history-title">Recent assessments</h2>
        </div>
        <p>Filter the newest evaluations, then open a row to inspect its full matched-rule evidence.</p>
      </div>

      <article v-if="!permissionEvaluations.assessmentRead.allowed" class="history-card permission-panel history-permission">
        <span aria-hidden="true">🔒</span>
        <h3>Assessment history is unavailable</h3>
        <p>{{ describePermissionRequirement(permissionEvaluations.assessmentRead) }}</p>
        <small>The backend remains the source of truth for authorization.</small>
      </article>

      <article v-else class="history-card">
        <form class="filter-bar" aria-label="Assessment filters" @submit.prevent="applyAssessmentFilters">
          <label>
            <span>Customer ID</span>
            <input v-model.trim="filters.customerId" placeholder="e.g. customer-101" autocomplete="off">
          </label>
          <label>
            <span>Risk level</span>
            <select v-model="filters.riskLevel">
              <option value="">All levels</option>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </label>
          <label>
            <span>Flag status</span>
            <select v-model="filters.flagged">
              <option value="">All decisions</option>
              <option value="true">Flagged</option>
              <option value="false">Not flagged</option>
            </select>
          </label>
          <label>
            <span>From</span>
            <input v-model="filters.from" type="date">
          </label>
          <label>
            <span>To</span>
            <input v-model="filters.to" type="date">
          </label>
          <div class="filter-actions">
            <button class="filter-button" type="submit" :disabled="historyLoading">
              {{ historyLoading ? 'Loading…' : 'Apply filters' }}
            </button>
            <button class="clear-button" type="button" :disabled="historyLoading" @click="clearAssessmentFilters">
              Clear
            </button>
          </div>
        </form>

        <div v-if="historyError" class="request-error history-error" :class="requestErrorCssClass(historyError)" role="alert">
          <div>
            <strong>{{ requestErrorTitle(historyError) }}</strong>
            <p>{{ historyError.message }}</p>
          </div>
          <button v-if="historyError.status === 401" type="button" @click="$emit('session-expired')">
            Sign in again
          </button>
          <button v-else type="button" @click="loadAssessmentPage(page.page)">Retry</button>
        </div>

        <div class="table-wrap" :aria-busy="historyLoading">
          <table>
            <caption>Fraud assessment results matching the current filters</caption>
            <thead>
              <tr>
                <th>Transaction</th>
                <th>Customer</th>
                <th>Score</th>
                <th>Risk</th>
                <th>Decision</th>
                <th>Evaluated</th>
                <th><span class="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody v-if="!historyLoading && assessments.length">
              <tr v-for="assessment in assessments" :key="assessment.assessmentId">
                <td>
                  <strong>{{ assessment.transactionId }}</strong>
                  <small>{{ abbreviateIdentifier(assessment.assessmentId) }}</small>
                </td>
                <td>{{ assessment.customerId }}</td>
                <td><strong class="score-cell">{{ assessment.riskScore }}</strong></td>
                <td>
                  <span class="table-risk" :class="riskLevelCssClass(assessment.riskLevel)">
                    {{ assessment.riskLevel }}
                  </span>
                </td>
                <td>
                  <span class="decision-pill" :class="assessment.flagged ? 'flagged' : 'clear'">
                    {{ assessment.flagged ? 'Flagged' : 'Clear' }}
                  </span>
                </td>
                <td>{{ formatDateTime(assessment.evaluatedAt) }}</td>
                <td>
                  <button class="inspect-button" type="button" @click="loadAssessmentDetails(assessment)">
                    Inspect
                  </button>
                </td>
              </tr>
            </tbody>
          </table>

          <div v-if="historyLoading" class="table-state" role="status">
            <span class="spinner" aria-hidden="true"></span>
            <p>Loading recent assessments…</p>
          </div>
          <div v-else-if="!assessments.length && !historyError" class="table-state">
            <span class="empty-table-mark" aria-hidden="true">0</span>
            <h3>No assessments found</h3>
            <p>Adjust the filters or process a new transaction event.</p>
          </div>
        </div>

        <footer class="pagination-bar">
          <p>
            Page <strong>{{ currentDisplayPageNumber }}</strong> of <strong>{{ totalDisplayPageCount }}</strong>
            <span>· {{ page.totalElements }} total</span>
          </p>
          <div>
            <button type="button" :disabled="historyLoading || page.page <= 0" @click="loadAssessmentPage(page.page - 1)">
              Previous
            </button>
            <button
              type="button"
              :disabled="historyLoading || page.page + 1 >= page.totalPages"
              @click="loadAssessmentPage(page.page + 1)"
            >
              Next
            </button>
          </div>
        </footer>
      </article>
    </section>

    <footer class="site-footer">
      <div class="brand footer-brand">
        <span aria-hidden="true">FR</span>
        <div>
          <strong>Fraud Rule Engine</strong>
          <small>Explainable decisions, one event at a time.</small>
        </div>
      </div>
      <p>The backend validates tokens, permissions, roles, payloads, and every final authorization decision.</p>
    </footer>
  </main>
</template>

<script>
import AssessmentResult from './AssessmentResult.vue'

function currentLocalDateTimeInputValue() {
  const now = new Date()
  const localDateTime = new Date(now.getTime() - now.getTimezoneOffset() * 60 * 1000)
  return localDateTime.toISOString().slice(0, 16)
}

function generateUniqueIdentifierSuffix() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`
}

function createDefaultTransaction() {
  const identifierSuffix = generateUniqueIdentifierSuffix()
  return {
    eventId: `evt-${identifierSuffix}`,
    transactionId: `txn-${identifierSuffix}`,
    customerId: 'customer-101',
    amount: '1250.00',
    currency: 'ZAR',
    category: 'GROCERIES',
    transactionType: 'CARD_PURCHASE',
    merchant: 'Market Square',
    country: 'ZA',
    customerCountry: 'ZA',
    transactionTime: currentLocalDateTimeInputValue()
  }
}

function createEmptyAssessmentPage() {
  return {
    content: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0
  }
}

function normalizeApiError(apiError) {
  const status = Number(apiError?.status) || 0
  let message = apiError?.message || 'The request could not be completed.'
  if (status === 401) {
    message = 'Your session is no longer authenticated. Sign in again to continue.'
  } else if (status === 403) {
    message = 'The backend denied this action. Your token may not contain the required entitlement and role.'
  }
  return {
    status,
    message,
    code: apiError?.payload?.code || '',
    fields: apiError?.fieldErrors || {}
  }
}

export default {
  name: 'DashboardView',
  components: {
    AssessmentResult
  },
  emits: ['logout', 'session-expired'],
  props: {
    apiClient: {
      type: Object,
      required: true
    },
    config: {
      type: Object,
      required: true
    },
    session: {
      type: Object,
      default: null
    },
    authenticationDisabled: {
      type: Boolean,
      required: true
    },
    permissionEvaluations: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      transaction: createDefaultTransaction(),
      filters: {
        customerId: '',
        riskLevel: '',
        flagged: '',
        from: '',
        to: ''
      },
      page: createEmptyAssessmentPage(),
      currentAssessment: null,
      submitting: false,
      historyLoading: false,
      resultLoading: false,
      submitError: null,
      historyError: null,
      resultError: null
    }
  },
  computed: {
    currentUserProfile() {
      return this.authenticationDisabled
        ? { displayName: 'Local operator', email: '', username: 'development' }
        : this.session?.profile || { displayName: 'Signed-in user', email: '', username: '' }
    },
    identitySubtitle() {
      if (this.authenticationDisabled) return 'Authentication disabled'
      const provider = String(this.config?.provider || '').toLowerCase()
      const roleClaims = provider === 'local' ? this.session?.roles : this.session?.groups
      if (roleClaims?.length) return roleClaims.join(' · ')
      return this.currentUserProfile.email || this.currentUserProfile.username || 'Authenticated session'
    },
    initials() {
      return String(this.currentUserProfile.displayName || 'FR')
        .split(/\s+/)
        .map((part) => part.charAt(0))
        .join('')
        .slice(0, 2)
        .toUpperCase()
    },
    assessments() {
      return Array.isArray(this.page.content) ? this.page.content : []
    },
    assessmentMetrics() {
      const highRisk = this.assessments.filter((item) => ['HIGH', 'CRITICAL'].includes(item.riskLevel)).length
      const flagged = this.assessments.filter((item) => item.flagged).length
      const scoreTotal = this.assessments.reduce((total, item) => total + Number(item.riskScore || 0), 0)
      const averageScore = this.assessments.length
        ? Math.round(scoreTotal / this.assessments.length)
        : 0
      return {
        total: this.page.totalElements || 0,
        highRisk,
        flagged,
        averageScore
      }
    },
    currentDisplayPageNumber() {
      return this.page.totalPages ? this.page.page + 1 : 0
    },
    totalDisplayPageCount() {
      return this.page.totalPages || 0
    }
  },
  mounted() {
    if (this.permissionEvaluations.assessmentRead.allowed) this.loadAssessmentPage(0)
  },
  methods: {
    permissionStatusSummary(permissionEvaluation) {
      if (this.authenticationDisabled) return 'Granted by local compatibility mode'
      const entitlement = permissionEvaluation.requiredScope ? 'Hosted scope' : 'Permission'
      const membership = permissionEvaluation.requiredScope ? 'Cognito group' : 'Role'
      if (permissionEvaluation.allowed) return `${entitlement} and ${membership.toLowerCase()} present`
      if (!permissionEvaluation.permissionGranted && !permissionEvaluation.roleGranted) {
        return `${entitlement} and ${membership.toLowerCase()} missing`
      }
      if (!permissionEvaluation.permissionGranted) return `Required ${entitlement.toLowerCase()} missing`
      return `Permitted ${membership.toLowerCase()} missing`
    },
    describePermissionRequirement(permissionEvaluation) {
      const roles = permissionEvaluation.requiredRoles.length
        ? permissionEvaluation.requiredRoles.join(', ')
        : 'no role restriction'
      if (permissionEvaluation.requiredScope) {
        return `Requires hosted scope “${permissionEvaluation.requiredScope}” for permission “${permissionEvaluation.requiredPermission}” and one Cognito group: ${roles}.`
      }
      return `Requires permission “${permissionEvaluation.requiredPermission}” and one role: ${roles}.`
    },
    resetTransactionForm() {
      this.transaction = createDefaultTransaction()
      this.submitError = null
    },
    buildTransactionRequestPayload() {
      return {
        eventId: this.transaction.eventId.trim(),
        transactionId: this.transaction.transactionId.trim(),
        customerId: this.transaction.customerId.trim(),
        amount: Number(this.transaction.amount),
        currency: this.transaction.currency.trim().toUpperCase(),
        category: this.transaction.category.trim().toUpperCase(),
        transactionType: this.transaction.transactionType.trim().toUpperCase(),
        merchant: this.transaction.merchant.trim(),
        country: this.transaction.country.trim().toUpperCase(),
        customerCountry: this.transaction.customerCountry.trim()
          ? this.transaction.customerCountry.trim().toUpperCase()
          : null,
        transactionTime: this.transaction.transactionTime
      }
    },
    async submitTransactionForAssessment() {
      this.submitting = true
      this.submitError = null
      this.resultError = null
      try {
        this.currentAssessment = await this.apiClient.post(
          '/api/v1/transaction-events',
          this.buildTransactionRequestPayload()
        )
        const customerId = this.transaction.customerId
        this.transaction = {
          ...createDefaultTransaction(),
          customerId
        }
        if (this.permissionEvaluations.assessmentRead.allowed) await this.loadAssessmentPage(0)
      } catch (error) {
        this.submitError = normalizeApiError(error)
      } finally {
        this.submitting = false
      }
    },
    fieldValidationError(fieldName) {
      return this.submitError?.fields?.[fieldName] || ''
    },
    buildAssessmentSearchQuery(requestedPageIndex) {
      const parameters = new URLSearchParams({
        page: String(Math.max(0, requestedPageIndex)),
        size: '10'
      })
      Object.entries(this.filters).forEach(([name, value]) => {
        if (value !== '') parameters.set(name, value)
      })
      return parameters.toString()
    },
    async loadAssessmentPage(requestedPageIndex = 0) {
      if (!this.permissionEvaluations.assessmentRead.allowed) return
      this.historyLoading = true
      this.historyError = null
      try {
        const response = await this.apiClient.get(
          `/api/v1/fraud-assessments?${this.buildAssessmentSearchQuery(requestedPageIndex)}`
        )
        this.page = {
          ...createEmptyAssessmentPage(),
          ...response,
          content: Array.isArray(response?.content) ? response.content : []
        }
      } catch (error) {
        this.historyError = normalizeApiError(error)
      } finally {
        this.historyLoading = false
      }
    },
    applyAssessmentFilters() {
      this.loadAssessmentPage(0)
    },
    clearAssessmentFilters() {
      this.filters = {
        customerId: '',
        riskLevel: '',
        flagged: '',
        from: '',
        to: ''
      }
      this.loadAssessmentPage(0)
    },
    async loadAssessmentDetails(assessmentSummary) {
      this.resultLoading = true
      this.resultError = null
      try {
        this.currentAssessment = await this.apiClient.get(
          `/api/v1/fraud-assessments/${encodeURIComponent(assessmentSummary.assessmentId)}`
        )
        document.getElementById('new-assessment')?.scrollIntoView({ behavior: 'smooth' })
      } catch (error) {
        this.resultError = normalizeApiError(error)
      } finally {
        this.resultLoading = false
      }
    },
    requestErrorTitle(requestErrorDetails) {
      if (requestErrorDetails.status === 401) return 'Session expired'
      if (requestErrorDetails.status === 403) return 'Backend authorization denied'
      if (requestErrorDetails.status === 409) return 'Event conflict'
      if (requestErrorDetails.status === 400) return 'Check the submitted values'
      if (requestErrorDetails.status === 0) return 'API unavailable'
      return requestErrorDetails.code || 'Request failed'
    },
    requestErrorCssClass(requestErrorDetails) {
      if (requestErrorDetails.status === 401) return 'authentication-error'
      if (requestErrorDetails.status === 403) return 'authorization-error'
      return ''
    },
    riskLevelCssClass(riskLevel) {
      return `risk-${String(riskLevel || 'unknown').toLowerCase()}`
    },
    abbreviateIdentifier(identifier) {
      const normalizedIdentifier = String(identifier || '')
      return normalizedIdentifier.length > 13
        ? `${normalizedIdentifier.slice(0, 8)}…${normalizedIdentifier.slice(-4)}`
        : normalizedIdentifier
    },
    formatDateTime(dateTimeValue) {
      if (!dateTimeValue) return '—'
      const date = new Date(dateTimeValue)
      if (Number.isNaN(date.getTime())) return dateTimeValue
      return date.toLocaleString(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short'
      })
    }
  }
}
</script>

<style scoped>
.dashboard-shell {
  min-height: 100vh;
  color: #172033;
  background: #f3f5f7;
}

.topbar {
  position: sticky;
  z-index: 40;
  top: 0;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto minmax(300px, 1fr);
  min-height: 72px;
  align-items: center;
  gap: 26px;
  padding: 9px max(24px, calc((100% - 1240px) / 2));
  border-bottom: 1px solid rgba(206, 214, 225, 0.9);
  background: rgba(250, 251, 252, 0.94);
  backdrop-filter: blur(18px);
}

.brand {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
  color: inherit;
  text-decoration: none;
}

.brand > span {
  display: grid;
  width: 39px;
  height: 39px;
  flex: 0 0 39px;
  place-items: center;
  border-radius: 12px;
  color: #fff;
  background: #17314e;
  font-size: 0.68rem;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.brand > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.brand strong {
  overflow: hidden;
  font-size: 0.84rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand small {
  color: #5f6b7d;
  font-size: 0.62rem;
}

.topbar nav {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid #e0e5eb;
  border-radius: 999px;
  background: #f2f5f8;
}

.topbar nav a {
  padding: 8px 13px;
  border-radius: 999px;
  color: #536176;
  font-size: 0.72rem;
  font-weight: 800;
  text-decoration: none;
}

.topbar nav a:hover,
.topbar nav a:focus-visible {
  color: #172f4c;
  background: #fff;
}

.account-area {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.environment-badge {
  padding: 5px 8px;
  border: 1px solid #bddbd3;
  border-radius: 999px;
  color: #17614e;
  background: #f0faf6;
  font-size: 0.61rem;
  font-weight: 900;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.environment-badge.local {
  border-color: #e5cc96;
  color: #795318;
  background: #fff9eb;
}

.identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.identity > span {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 50%;
  color: #eaf3f8;
  background: #2e5b73;
  font-size: 0.66rem;
  font-weight: 900;
}

.identity > div {
  display: grid;
  min-width: 0;
  max-width: 170px;
  gap: 2px;
}

.identity strong,
.identity small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity strong {
  font-size: 0.7rem;
}

.identity small {
  color: #5f6b7d;
  font-size: 0.57rem;
}

.logout-button,
.text-button,
.clear-button,
.inspect-button,
.pagination-bar button,
.request-error button {
  border: 1px solid #d3dae3;
  color: #39485e;
  background: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 0.68rem;
  font-weight: 800;
}

.logout-button {
  min-height: 34px;
  padding: 0 10px;
  border-radius: 8px;
}

.local-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 37px;
  padding: 7px 20px;
  color: #704f1b;
  background: #fff5d9;
  font-size: 0.7rem;
}

.local-banner span {
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #a7741f;
  font-size: 0.62rem;
  font-weight: 900;
}

.local-banner p {
  margin: 0;
}

.hero-section,
.metric-grid,
.workspace-section,
.history-section,
.site-footer {
  width: min(1240px, calc(100% - 48px));
  margin-right: auto;
  margin-left: auto;
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(350px, 0.75fr);
  gap: clamp(44px, 7vw, 96px);
  align-items: center;
  min-height: 490px;
  padding: 70px 0 58px;
}

.eyebrow {
  margin: 0 0 11px;
  color: #27776f;
  font-size: 0.68rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin: 0 0 22px;
  color: #122038;
  font-size: clamp(3.6rem, 6.5vw, 6.5rem);
  font-weight: 750;
  letter-spacing: -0.073em;
  line-height: 0.9;
}

.hero-copy > p:not(.eyebrow) {
  max-width: 680px;
  margin: 0;
  color: #5f6b7d;
  font-size: 1.02rem;
  line-height: 1.72;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 29px;
}

.primary-link,
.secondary-link {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  padding: 0 17px;
  border-radius: 9px;
  font-size: 0.76rem;
  font-weight: 850;
  text-decoration: none;
}

.primary-link {
  color: #fff;
  background: #17314e;
  box-shadow: 0 12px 25px rgba(23, 49, 78, 0.15);
}

.secondary-link {
  border: 1px solid #cad2dd;
  color: #304159;
  background: #fff;
}

.access-card {
  overflow: hidden;
  border: 1px solid #d6dde6;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 22px 70px rgba(25, 36, 55, 0.1);
}

.access-card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
  border-bottom: 1px solid #e4e8ee;
}

.access-card h2,
.section-intro h2 {
  margin: 0;
  color: #152139;
  letter-spacing: -0.04em;
}

.access-card h2 {
  font-size: 1.2rem;
}

.connection-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #36a276;
  box-shadow: 0 0 0 6px rgba(54, 162, 118, 0.12);
}

.access-list {
  display: grid;
  padding: 8px 24px;
}

.access-list > div {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 0;
  border-bottom: 1px solid #edf0f4;
}

.access-list > div:last-child {
  border-bottom: 0;
}

.access-icon {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 9px;
  font-size: 0.72rem;
  font-weight: 900;
}

.access-icon.granted {
  color: #176348;
  background: #e8f7f0;
}

.access-icon.denied {
  color: #9a3440;
  background: #fcecef;
}

.access-list p {
  display: grid;
  margin: 0;
  gap: 3px;
}

.access-list strong {
  color: #2b374c;
  font-size: 0.77rem;
}

.access-list small {
  color: #5f6b7d;
  font-size: 0.67rem;
}

.backend-authority {
  margin: 0;
  padding: 15px 24px;
  color: #5f6b7d;
  background: #f7f9fb;
  font-size: 0.68rem;
  line-height: 1.52;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 13px;
  padding-bottom: 82px;
}

.metric-grid article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 13px;
  align-items: start;
  padding: 19px;
  border: 1px solid #dce2e9;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.86);
}

.metric-icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 10px;
  font-size: 0.74rem;
  font-weight: 900;
}

.metric-icon.navy { color: #244b6d; background: #e7f0f7; }
.metric-icon.red { color: #a93540; background: #fbecef; }
.metric-icon.amber { color: #96631a; background: #fff3d9; }
.metric-icon.teal { color: #1f7167; background: #e6f5f2; }

.metric-grid small {
  display: block;
  color: #5f6b7d;
  font-size: 0.66rem;
  font-weight: 800;
  text-transform: uppercase;
}

.metric-grid strong {
  display: block;
  margin: 4px 0 3px;
  color: #172238;
  font-size: 1.75rem;
  line-height: 1;
}

.metric-grid p {
  margin: 0;
  color: #5f6b7d;
  font-size: 0.62rem;
}

.workspace-section,
.history-section {
  scroll-margin-top: 92px;
}

.workspace-section {
  padding-bottom: 94px;
}

.section-intro {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.72fr);
  gap: 42px;
  align-items: end;
  margin-bottom: 24px;
}

.section-intro h2 {
  font-size: clamp(2rem, 4vw, 3.2rem);
}

.section-intro > p {
  margin: 0;
  color: #5f6b7d;
  font-size: 0.85rem;
  line-height: 1.63;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(520px, 1.12fr) minmax(390px, 0.88fr);
  gap: 20px;
  align-items: start;
}

.form-card,
.history-card {
  overflow: hidden;
  border: 1px solid #d8dde6;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(24, 34, 52, 0.07);
}

.card-heading {
  display: flex;
  min-height: 91px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 25px;
  border-bottom: 1px solid #e5e9ef;
}

.card-heading > div {
  display: flex;
  align-items: center;
  gap: 11px;
}

.card-heading > div > div {
  display: grid;
  gap: 3px;
}

.card-heading strong {
  color: #1c293e;
  font-size: 0.84rem;
}

.card-heading small {
  color: #5f6b7d;
  font-size: 0.66rem;
}

.step-number {
  display: grid;
  width: 33px;
  height: 33px;
  place-items: center;
  border-radius: 10px;
  color: #fff;
  background: #24746c;
  font-size: 0.72rem;
  font-weight: 900;
}

.text-button {
  min-height: 32px;
  padding: 0 10px;
  border-radius: 8px;
}

.transaction-form {
  padding: 24px 25px 27px;
}

fieldset {
  min-width: 0;
  margin: 0 0 24px;
  padding: 0;
  border: 0;
}

legend {
  width: 100%;
  margin-bottom: 12px;
  color: #657287;
  font-size: 0.66rem;
  font-weight: 900;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-grid.three-columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field-grid label,
.filter-bar label {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.field-grid label > span,
.filter-bar label > span {
  color: #445269;
  font-size: 0.68rem;
  font-weight: 800;
}

.field-grid input,
.filter-bar input,
.filter-bar select {
  width: 100%;
  min-width: 0;
  border: 1px solid #cfd6df;
  border-radius: 9px;
  color: #1d2a3f;
  background: #fbfcfd;
  font: inherit;
  font-size: 0.76rem;
}

.field-grid input {
  min-height: 44px;
  padding: 0 11px;
}

.field-grid input:hover,
.filter-bar input:hover,
.filter-bar select:hover {
  border-color: #aebac8;
}

.field-grid input:focus,
.filter-bar input:focus,
.filter-bar select:focus {
  border-color: #2b766e;
  outline: 3px solid rgba(43, 118, 110, 0.13);
  background: #fff;
}

.wide-field {
  grid-column: span 1;
}

.field-error {
  color: #b42332;
  font-size: 0.64rem;
  line-height: 1.4;
}

.submit-button {
  display: flex;
  width: 100%;
  min-height: 50px;
  align-items: center;
  justify-content: space-between;
  padding: 0 17px;
  border: 0;
  border-radius: 10px;
  color: #fff;
  background: #17314e;
  cursor: pointer;
  font: inherit;
  font-size: 0.79rem;
  font-weight: 850;
  box-shadow: 0 12px 25px rgba(23, 49, 78, 0.15);
}

.submit-button:hover:not(:disabled) {
  background: #245578;
}

.submit-button b {
  font-size: 1rem;
}

button:focus-visible,
a:focus-visible {
  outline: 3px solid rgba(37, 127, 117, 0.28);
  outline-offset: 2px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.permission-panel {
  display: grid;
  min-height: 420px;
  place-content: center;
  justify-items: center;
  padding: 36px;
  text-align: center;
}

.permission-panel > span {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 16px;
  background: #f1f4f7;
  font-size: 1.1rem;
}

.permission-panel h3 {
  margin: 17px 0 7px;
  color: #1d293d;
  font-size: 1.1rem;
}

.permission-panel p {
  max-width: 450px;
  margin: 0;
  color: #5f6b7d;
  font-size: 0.8rem;
  line-height: 1.55;
}

.permission-panel small {
  margin-top: 8px;
  color: #5f6b7d;
  font-size: 0.66rem;
}

.result-column {
  position: sticky;
  top: 92px;
  min-width: 0;
}

.result-loading {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 10px;
  padding: 12px 14px;
  border: 1px solid #cbdce8;
  border-radius: 11px;
  color: #315b75;
  background: #f2f8fc;
  font-size: 0.73rem;
  font-weight: 750;
}

.result-loading span,
.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: spin 0.75s linear infinite;
}

.request-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 13px;
  margin: 0 0 14px;
  padding: 12px 13px;
  border: 1px solid #efc9cc;
  border-radius: 10px;
  color: #952f3a;
  background: #fff4f5;
}

.request-error.authorization-error {
  border-color: #ebd09b;
  color: #7b561d;
  background: #fff9ed;
}

.request-error.authentication-error {
  border-color: #c7d9e7;
  color: #2d5b79;
  background: #f2f8fc;
}

.request-error strong {
  display: block;
  margin-bottom: 3px;
  font-size: 0.76rem;
}

.request-error p {
  margin: 0;
  font-size: 0.69rem;
  line-height: 1.45;
}

.request-error button {
  min-height: 30px;
  flex: 0 0 auto;
  padding: 0 9px;
  border-radius: 7px;
}

.result-request-error {
  margin-bottom: 10px;
}

.history-section {
  padding-bottom: 96px;
}

.history-intro {
  padding-top: 6px;
}

.history-permission {
  min-height: 330px;
}

.filter-bar {
  display: grid;
  grid-template-columns: 1.2fr repeat(4, minmax(120px, 0.8fr)) auto;
  gap: 10px;
  align-items: end;
  padding: 19px 20px;
  border-bottom: 1px solid #e3e8ee;
  background: #f8fafc;
}

.filter-bar input,
.filter-bar select {
  min-height: 40px;
  padding: 0 9px;
}

.filter-actions {
  display: flex;
  gap: 6px;
}

.filter-button,
.clear-button {
  min-height: 40px;
  padding: 0 11px;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-size: 0.68rem;
  font-weight: 800;
}

.filter-button {
  border: 0;
  color: #fff;
  background: #17314e;
}

.history-error {
  margin: 14px 18px 0;
}

.table-wrap {
  position: relative;
  min-height: 390px;
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

caption,
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

th,
td {
  padding: 14px 16px;
  border-bottom: 1px solid #edf0f4;
  white-space: nowrap;
}

th {
  color: #5f6b7d;
  background: #fff;
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}

td {
  color: #4a586d;
  font-size: 0.72rem;
}

td:first-child {
  display: grid;
  gap: 3px;
}

td:first-child strong {
  color: #213047;
  font-size: 0.74rem;
}

td:first-child small {
  color: #5f6b7d;
  font-size: 0.6rem;
}

tbody tr:hover {
  background: #fafbfd;
}

.score-cell {
  color: #1d2b42;
  font-size: 0.9rem;
}

.table-risk,
.decision-pill {
  display: inline-flex;
  padding: 5px 7px;
  border-radius: 999px;
  font-size: 0.59rem;
  font-weight: 900;
  text-transform: uppercase;
}

.table-risk {
  border: 1px solid currentColor;
  background: #fff;
}

.decision-pill.flagged {
  color: #a22e3b;
  background: #fcecef;
}

.decision-pill.clear {
  color: #1d6c4f;
  background: #eaf8f1;
}

.inspect-button {
  min-height: 30px;
  padding: 0 9px;
  border-radius: 7px;
}

.table-state {
  position: absolute;
  inset: 45px 0 0;
  display: grid;
  place-content: center;
  justify-items: center;
  color: #5f6b7d;
  background: rgba(255, 255, 255, 0.93);
  text-align: center;
}

.table-state .spinner {
  margin-bottom: 10px;
  color: #2b766e;
}

.table-state h3 {
  margin: 12px 0 5px;
  color: #263349;
  font-size: 0.92rem;
}

.table-state p {
  margin: 0;
  font-size: 0.72rem;
}

.empty-table-mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 13px;
  color: #617087;
  background: #edf1f5;
  font-size: 0.82rem;
  font-weight: 900;
}

.pagination-bar {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 10px 18px;
  border-top: 1px solid #e5e9ee;
}

.pagination-bar p {
  margin: 0;
  color: #5f6b7d;
  font-size: 0.68rem;
}

.pagination-bar p span {
  color: #5f6b7d;
}

.pagination-bar > div {
  display: flex;
  gap: 7px;
}

.pagination-bar button {
  min-height: 32px;
  padding: 0 10px;
  border-radius: 8px;
}

.risk-low { color: #25845f; }
.risk-medium { color: #945200; }
.risk-high { color: #c44236; }
.risk-critical { color: #8b2635; }
.risk-unknown { color: #64748b; }

.site-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 27px 0 36px;
  border-top: 1px solid #d7dde5;
}

.footer-brand > span {
  width: 34px;
  height: 34px;
  flex-basis: 34px;
  border-radius: 10px;
}

.site-footer > p {
  max-width: 550px;
  margin: 0;
  color: #5f6b7d;
  font-size: 0.67rem;
  line-height: 1.5;
  text-align: right;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 1120px) {
  .topbar {
    grid-template-columns: minmax(200px, 1fr) auto;
  }

  .topbar nav {
    display: none;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .result-column {
    position: static;
  }

  .filter-bar {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .hero-section {
    grid-template-columns: 1fr;
    gap: 38px;
  }

  .access-card {
    max-width: 600px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .section-intro {
    grid-template-columns: 1fr;
    gap: 10px;
  }
}

@media (max-width: 720px) {
  .topbar {
    grid-template-columns: 1fr auto;
    padding-right: 16px;
    padding-left: 16px;
  }

  .environment-badge,
  .identity > div {
    display: none;
  }

  .hero-section,
  .metric-grid,
  .workspace-section,
  .history-section,
  .site-footer {
    width: min(100% - 28px, 1240px);
  }

  .hero-section {
    min-height: auto;
    padding: 58px 0 48px;
  }

  .hero-copy h1 {
    font-size: 3.8rem;
  }

  .field-grid.three-columns {
    grid-template-columns: 1fr;
  }

  .filter-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-actions {
    align-self: stretch;
  }

  .filter-actions button {
    flex: 1;
  }

  .site-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .site-footer > p {
    text-align: left;
  }
}

@media (max-width: 520px) {
  .brand small,
  .logout-button {
    display: none;
  }

  .hero-copy h1 {
    font-size: 3.25rem;
  }

  .metric-grid,
  .field-grid,
  .filter-bar {
    grid-template-columns: 1fr;
  }

  .workspace-section {
    padding-bottom: 74px;
  }

  .card-heading,
  .transaction-form {
    padding-right: 18px;
    padding-left: 18px;
  }

  .pagination-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
