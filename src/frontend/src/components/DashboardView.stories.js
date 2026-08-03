import DashboardView from './DashboardView.vue'

const assessments = [
  {
    assessmentId: 'assessment-storybook-1001',
    eventId: 'evt-storybook-1001',
    transactionId: 'txn-storybook-1001',
    customerId: 'customer-101',
    amount: 24999.95,
    currency: 'ZAR',
    category: 'ELECTRONICS',
    transactionType: 'CARD_PURCHASE',
    merchant: 'Cape Digital Market',
    country: 'NG',
    customerCountry: 'ZA',
    evaluatedAt: '2026-08-03T08:42:15',
    flagged: true,
    riskScore: 65,
    riskLevel: 'HIGH',
    matchedRules: [
      {
        ruleCode: 'HIGH_VALUE_TRANSACTION',
        reason: 'Transaction amount exceeds the configured high-value threshold.',
        score: 40
      },
      {
        ruleCode: 'FOREIGN_TRANSACTION',
        reason: 'Merchant and customer countries do not match.',
        score: 25
      }
    ]
  },
  {
    assessmentId: 'assessment-storybook-1002',
    eventId: 'evt-storybook-1002',
    transactionId: 'txn-storybook-1002',
    customerId: 'customer-204',
    amount: 289.5,
    currency: 'ZAR',
    category: 'GROCERIES',
    transactionType: 'CARD_PURCHASE',
    merchant: 'Market Square',
    country: 'ZA',
    customerCountry: 'ZA',
    evaluatedAt: '2026-08-03T08:35:02',
    flagged: false,
    riskScore: 0,
    riskLevel: 'LOW',
    matchedRules: []
  },
  {
    assessmentId: 'assessment-storybook-1003',
    eventId: 'evt-storybook-1003',
    transactionId: 'txn-storybook-1003',
    customerId: 'customer-318',
    amount: 78000,
    currency: 'ZAR',
    category: 'CRYPTOCURRENCY',
    transactionType: 'TRANSFER',
    merchant: 'International Crypto Exchange',
    country: 'US',
    customerCountry: 'ZA',
    evaluatedAt: '2026-08-03T08:21:47',
    flagged: true,
    riskScore: 80,
    riskLevel: 'CRITICAL',
    matchedRules: [
      {
        ruleCode: 'HIGH_VALUE_TRANSACTION',
        reason: 'Transaction amount exceeds the configured high-value threshold.',
        score: 40
      },
      {
        ruleCode: 'RISKY_TRANSACTION_CATEGORY',
        reason: 'The merchant category is configured for enhanced review.',
        score: 15
      },
      {
        ruleCode: 'FOREIGN_TRANSACTION',
        reason: 'Merchant and customer countries do not match.',
        score: 25
      }
    ]
  }
]

const page = {
  content: assessments,
  page: 0,
  size: 10,
  totalElements: assessments.length,
  totalPages: 1
}

function createStoryApiClient({ assessmentPage = page, assessmentDetails = assessments[0] } = {}) {
  return {
    async get(requestPath) {
      if (requestPath.startsWith('/api/v1/fraud-assessments?')) return assessmentPage
      if (requestPath.startsWith('/api/v1/fraud-assessments/')) return assessmentDetails
      throw new Error(`Unexpected Storybook GET request: ${requestPath}`)
    },
    async post(requestPath) {
      if (requestPath === '/api/v1/transaction-events') return assessmentDetails
      throw new Error(`Unexpected Storybook POST request: ${requestPath}`)
    }
  }
}

const grantedPermission = {
  allowed: true,
  permissionGranted: true,
  roleGranted: true,
  requiredPermission: 'ASSESSMENT_READ',
  requiredScope: '',
  requiredRoles: ['ADMIN', 'ANALYST', 'AUDITOR']
}

const deniedPermission = {
  allowed: false,
  permissionGranted: false,
  roleGranted: false,
  requiredPermission: 'TRANSACTION_WRITE',
  requiredScope: '',
  requiredRoles: ['ADMIN', 'OPERATOR']
}

const localConfig = {
  provider: 'local'
}

const localSession = {
  profile: {
    displayName: 'Anele Dlamini',
    email: 'anele.dlamini@example.test',
    username: 'anele.dlamini'
  },
  roles: ['ADMIN'],
  permissions: ['TRANSACTION_WRITE', 'ASSESSMENT_READ']
}

const authenticatedArgs = {
  apiClient: createStoryApiClient(),
  config: localConfig,
  session: localSession,
  authenticationDisabled: false,
  permissionEvaluations: {
    transactionWrite: {
      ...grantedPermission,
      requiredPermission: 'TRANSACTION_WRITE',
      requiredRoles: ['ADMIN', 'OPERATOR']
    },
    assessmentRead: grantedPermission
  }
}

const deterministicTransaction = {
  eventId: 'evt-storybook-new-1004',
  transactionId: 'txn-storybook-new-1004',
  customerId: 'customer-101',
  amount: '1250.00',
  currency: 'ZAR',
  category: 'GROCERIES',
  transactionType: 'CARD_PURCHASE',
  merchant: 'Market Square',
  country: 'ZA',
  customerCountry: 'ZA',
  transactionTime: '2026-08-03T10:30'
}

export default {
  title: 'Dashboard/DashboardView',
  component: DashboardView,
  render: (args) => ({
    components: { DashboardView },
    setup() {
      return { args }
    },
    template: '<DashboardView ref="dashboard" v-bind="args" />',
    mounted() {
      this.$refs.dashboard.transaction = { ...deterministicTransaction }
    }
  }),
  parameters: {
    layout: 'fullscreen'
  },
  argTypes: {
    apiClient: { control: false },
    config: { control: false },
    session: { control: false },
    permissionEvaluations: { control: false },
    onLogout: { action: 'logout' },
    onSessionExpired: { action: 'session-expired' }
  }
}

export const AuthenticatedAdministrator = {
  args: authenticatedArgs
}

export const ReadOnlyAnalyst = {
  args: {
    ...authenticatedArgs,
    apiClient: createStoryApiClient(),
    session: {
      ...localSession,
      profile: {
        displayName: 'Anele Dlamini',
        email: 'anele.dlamini@example.test',
        username: 'anele.dlamini'
      },
      roles: ['ANALYST'],
      permissions: ['ASSESSMENT_READ']
    },
    permissionEvaluations: {
      transactionWrite: deniedPermission,
      assessmentRead: grantedPermission
    }
  }
}

export const AuthenticationDisabledCompatibilityMode = {
  args: {
    ...authenticatedArgs,
    apiClient: createStoryApiClient(),
    session: null,
    authenticationDisabled: true
  }
}

export const NoGrantedPermissions = {
  args: {
    ...authenticatedArgs,
    apiClient: createStoryApiClient(),
    session: {
      ...localSession,
      roles: [],
      permissions: []
    },
    permissionEvaluations: {
      transactionWrite: deniedPermission,
      assessmentRead: {
        ...deniedPermission,
        requiredPermission: 'ASSESSMENT_READ',
        requiredRoles: ['ADMIN', 'ANALYST', 'AUDITOR']
      }
    }
  }
}

export const EmptyAssessmentHistory = {
  args: {
    ...authenticatedArgs,
    apiClient: createStoryApiClient({
      assessmentPage: {
        content: [],
        page: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0
      }
    })
  }
}

export const HistoryApiUnavailable = {
  args: {
    ...authenticatedArgs,
    apiClient: {
      async get() {
        throw Object.assign(new Error('The application API could not be reached.'), { status: 0 })
      },
      async post() {
        return assessments[0]
      }
    }
  }
}
