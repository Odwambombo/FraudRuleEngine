import AssessmentResult from './AssessmentResult.vue'

const baseAssessment = {
  assessmentId: 'assessment-01J4A5M9F6W2YQ83K7P1D0N6RT',
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
}

export default {
  title: 'Assessments/AssessmentResult',
  component: AssessmentResult,
  decorators: [
    () => ({
      template: '<div style="width: min(720px, calc(100vw - 32px));"><story /></div>'
    })
  ],
  parameters: {
    layout: 'centered'
  },
  argTypes: {
    assessment: { control: 'object' }
  }
}

export const Empty = {
  args: {
    assessment: null
  }
}

export const HighRiskFlagged = {
  args: {
    assessment: baseAssessment
  }
}

export const CriticalRiskFlagged = {
  args: {
    assessment: {
      ...baseAssessment,
      transactionId: 'txn-storybook-critical',
      amount: 78000,
      merchant: 'International Crypto Exchange',
      category: 'CRYPTOCURRENCY',
      riskScore: 115,
      riskLevel: 'CRITICAL',
      matchedRules: [
        ...baseAssessment.matchedRules,
        {
          ruleCode: 'RISKY_TRANSACTION_CATEGORY',
          reason: 'The merchant category is configured for enhanced review.',
          score: 15
        },
        {
          ruleCode: 'TRANSACTION_VELOCITY',
          reason: 'The customer submitted more transactions than the configured velocity limit.',
          score: 35
        }
      ]
    }
  }
}

export const MediumRiskCleared = {
  args: {
    assessment: {
      ...baseAssessment,
      transactionId: 'txn-storybook-medium',
      amount: 5400,
      merchant: 'International Gaming Merchant',
      category: 'GAMBLING',
      country: 'US',
      flagged: false,
      riskScore: 40,
      riskLevel: 'MEDIUM',
      matchedRules: [
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
  }
}

export const LowRiskWithNoRuleMatches = {
  args: {
    assessment: {
      ...baseAssessment,
      transactionId: 'txn-storybook-low',
      amount: 289.5,
      category: 'GROCERIES',
      merchant: 'Market Square',
      country: 'ZA',
      flagged: false,
      riskScore: 0,
      riskLevel: 'LOW',
      matchedRules: []
    }
  }
}

export const IncompleteLocationContext = {
  args: {
    assessment: {
      ...baseAssessment,
      transactionId: 'txn-storybook-location-unknown',
      country: null,
      customerCountry: null
    }
  }
}
