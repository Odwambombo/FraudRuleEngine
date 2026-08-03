<template>
  <article class="result-card" aria-live="polite">
    <div class="card-heading">
      <div>
        <p class="section-kicker">Decision output</p>
        <h2>Assessment result</h2>
      </div>
      <span v-if="assessment" class="risk-badge" :class="riskLevelCssClass(assessment.riskLevel)">
        {{ assessment.riskLevel }} risk
      </span>
    </div>

    <div v-if="!assessment" class="result-empty">
      <span class="result-empty-mark" aria-hidden="true">FR</span>
      <h3>No assessment selected</h3>
      <p>Process a transaction or open a recent assessment to see its decision evidence.</p>
    </div>

    <div v-else class="result-content">
      <div class="decision-summary">
        <div class="score-orbit" :class="riskLevelCssClass(assessment.riskLevel)">
          <strong>{{ assessment.riskScore }}</strong>
          <span>risk score</span>
        </div>
        <div class="decision-copy">
          <p class="decision-status" :class="assessment.flagged ? 'flagged' : 'cleared'">
            <span aria-hidden="true"></span>
            {{ assessment.flagged ? 'Flagged for review' : 'No review flag' }}
          </p>
          <h3>{{ assessment.transactionId }}</h3>
          <p>
            {{ formatMoney(assessment.amount, assessment.currency) }} at
            {{ assessment.merchant || 'Unknown merchant' }}
          </p>
          <small>Evaluated {{ formatDateTime(assessment.evaluatedAt) }}</small>
        </div>
      </div>

      <dl class="result-facts">
        <div>
          <dt>Customer</dt>
          <dd>{{ assessment.customerId }}</dd>
        </div>
        <div>
          <dt>Category</dt>
          <dd>{{ assessment.category || '—' }}</dd>
        </div>
        <div>
          <dt>Transaction type</dt>
          <dd>{{ assessment.transactionType || '—' }}</dd>
        </div>
        <div>
          <dt>Route</dt>
          <dd>{{ countryRouteLabel }}</dd>
        </div>
      </dl>

      <section class="rule-section" aria-labelledby="matched-rules-title">
        <div class="rule-heading">
          <div>
            <p class="section-kicker">Decision evidence</p>
            <h3 id="matched-rules-title">Matched rules</h3>
          </div>
          <span>{{ matchedRuleResults.length }}</span>
        </div>

        <div v-if="matchedRuleResults.length" class="rule-list">
          <article v-for="rule in matchedRuleResults" :key="`${rule.ruleCode}-${rule.score}`" class="rule-item">
            <div>
              <strong>{{ formatRuleCode(rule.ruleCode) }}</strong>
              <code>{{ rule.ruleCode }}</code>
            </div>
            <p>{{ rule.reason }}</p>
            <span>+{{ rule.score }}</span>
          </article>
        </div>
        <div v-else class="no-rules">
          <span aria-hidden="true">✓</span>
          <p>No configured fraud rule contributed to this score.</p>
        </div>
      </section>
    </div>
  </article>
</template>

<script>
export default {
  name: 'AssessmentResult',
  props: {
    assessment: {
      type: Object,
      default: null
    }
  },
  computed: {
    matchedRuleResults() {
      return Array.isArray(this.assessment?.matchedRules) ? this.assessment.matchedRules : []
    },
    countryRouteLabel() {
      if (!this.assessment) return '—'
      const origin = this.assessment.customerCountry || 'Unknown'
      const destination = this.assessment.country || 'Unknown'
      return `${origin} → ${destination}`
    }
  },
  methods: {
    riskLevelCssClass(riskLevel) {
      return `risk-${String(riskLevel || 'unknown').toLowerCase()}`
    },
    formatRuleCode(ruleCode) {
      return String(ruleCode || 'Rule match')
        .toLowerCase()
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ')
    },
    formatMoney(amount, currency) {
      const numericAmount = Number(amount)
      if (!Number.isFinite(numericAmount)) return `${amount || '—'} ${currency || ''}`.trim()
      try {
        return new Intl.NumberFormat(undefined, {
          style: 'currency',
          currency: currency || 'ZAR',
          maximumFractionDigits: 2
        }).format(numericAmount)
      } catch (error) {
        return `${numericAmount.toFixed(2)} ${currency || ''}`.trim()
      }
    },
    formatDateTime(dateTimeValue) {
      if (!dateTimeValue) return '—'
      const date = new Date(dateTimeValue)
      return Number.isNaN(date.getTime()) ? dateTimeValue : date.toLocaleString()
    }
  }
}
</script>

<style scoped>
.result-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #d8dde6;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(24, 34, 52, 0.08);
}

.card-heading,
.rule-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.card-heading {
  min-height: 91px;
  padding: 22px 26px;
  border-bottom: 1px solid #e5e9ef;
}

.section-kicker {
  margin: 0 0 5px;
  color: #64748b;
  font-size: 0.69rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h2,
h3,
p {
  margin-top: 0;
}

h2 {
  margin-bottom: 0;
  color: #152033;
  font-size: 1.28rem;
  letter-spacing: -0.025em;
}

.risk-badge {
  padding: 7px 10px;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 0.69rem;
  font-weight: 900;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.result-empty {
  display: grid;
  min-height: 440px;
  place-content: center;
  justify-items: center;
  padding: 38px;
  text-align: center;
}

.result-empty-mark {
  display: grid;
  width: 62px;
  height: 62px;
  place-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 18px;
  color: #1e4d72;
  background: #eff6fb;
  font-size: 0.86rem;
  font-weight: 900;
  letter-spacing: 0.06em;
}

.result-empty h3 {
  margin: 18px 0 7px;
  color: #172033;
  font-size: 1.15rem;
}

.result-empty p {
  max-width: 390px;
  margin-bottom: 0;
  color: #6b778c;
  line-height: 1.6;
}

.result-content {
  padding: 26px;
}

.decision-summary {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 24px;
  align-items: center;
}

.score-orbit {
  display: grid;
  width: 112px;
  height: 112px;
  place-content: center;
  border: 9px solid currentColor;
  border-radius: 50%;
  background: #f8fafc;
  text-align: center;
}

.score-orbit strong {
  color: #142033;
  font-size: 2.1rem;
  line-height: 1;
}

.score-orbit span {
  margin-top: 5px;
  color: #5f6b7d;
  font-size: 0.66rem;
  font-weight: 800;
  text-transform: uppercase;
}

.decision-copy {
  min-width: 0;
}

.decision-status {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  font-size: 0.73rem;
  font-weight: 900;
  text-transform: uppercase;
}

.decision-status span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.decision-status.flagged {
  color: #b42332;
}

.decision-status.cleared {
  color: #1f7a56;
}

.decision-copy h3 {
  overflow: hidden;
  margin-bottom: 6px;
  color: #142033;
  font-size: 1.45rem;
  letter-spacing: -0.03em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.decision-copy > p:not(.decision-status) {
  margin-bottom: 7px;
  color: #46546a;
  line-height: 1.5;
}

.decision-copy small {
  color: #5f6b7d;
}

.result-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  margin: 27px 0;
  border: 1px solid #e1e6ed;
  border-radius: 14px;
  background: #e1e6ed;
}

.result-facts div {
  min-width: 0;
  padding: 14px 16px;
  background: #f8fafc;
}

.result-facts dt {
  margin-bottom: 4px;
  color: #5f6b7d;
  font-size: 0.65rem;
  font-weight: 800;
  text-transform: uppercase;
}

.result-facts dd {
  overflow: hidden;
  margin: 0;
  color: #253148;
  font-size: 0.83rem;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-section {
  padding-top: 2px;
}

.rule-heading {
  margin-bottom: 13px;
}

.rule-heading h3 {
  margin: 0;
  color: #172033;
  font-size: 1rem;
}

.rule-heading > span {
  display: grid;
  min-width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 9px;
  color: #fff;
  background: #1d3557;
  font-size: 0.75rem;
  font-weight: 900;
}

.rule-list {
  display: grid;
  gap: 10px;
}

.rule-item {
  display: grid;
  grid-template-columns: minmax(120px, 0.9fr) minmax(0, 1.6fr) auto;
  gap: 14px;
  align-items: center;
  padding: 13px 14px;
  border: 1px solid #e1e6ed;
  border-radius: 12px;
  background: #fbfcfe;
}

.rule-item > div {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.rule-item strong {
  color: #253148;
  font-size: 0.8rem;
}

.rule-item code {
  overflow: hidden;
  color: #5f6b7d;
  font-size: 0.62rem;
  text-overflow: ellipsis;
}

.rule-item p {
  margin-bottom: 0;
  color: #647084;
  font-size: 0.76rem;
  line-height: 1.48;
}

.rule-item > span {
  color: #b42332;
  font-size: 0.88rem;
  font-weight: 900;
}

.no-rules {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px;
  border: 1px solid #cde7db;
  border-radius: 12px;
  color: #176344;
  background: #f1faf6;
}

.no-rules span {
  display: grid;
  width: 27px;
  height: 27px;
  flex: 0 0 27px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #25845f;
  font-size: 0.75rem;
  font-weight: 900;
}

.no-rules p {
  margin: 0;
  font-size: 0.8rem;
}

.risk-low {
  color: #25845f;
}

.risk-medium {
  color: #945200;
}

.risk-high {
  color: #c44236;
}

.risk-critical {
  color: #8b2635;
}

.risk-unknown {
  color: #64748b;
}

@media (max-width: 560px) {
  .card-heading,
  .result-content {
    padding-right: 18px;
    padding-left: 18px;
  }

  .decision-summary {
    grid-template-columns: 86px minmax(0, 1fr);
    gap: 17px;
  }

  .score-orbit {
    width: 86px;
    height: 86px;
    border-width: 7px;
  }

  .score-orbit strong {
    font-size: 1.65rem;
  }

  .result-facts {
    grid-template-columns: 1fr;
  }

  .rule-item {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .rule-item p {
    grid-column: 1 / -1;
  }
}
</style>
