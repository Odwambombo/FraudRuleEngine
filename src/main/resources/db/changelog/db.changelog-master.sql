--liquibase formatted sql

--changeset odwambombo:001-create-transaction-event
CREATE TABLE transaction_event (
    id UUID NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    category VARCHAR(64) NOT NULL,
    transaction_type VARCHAR(64) NOT NULL,
    merchant VARCHAR(255) NOT NULL,
    country VARCHAR(2) NOT NULL,
    customer_country VARCHAR(2),
    transaction_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_transaction_event PRIMARY KEY (id),
    CONSTRAINT uk_transaction_event_event_id UNIQUE (event_id),
    CONSTRAINT ck_transaction_event_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_transaction_event_transaction_id
    ON transaction_event (transaction_id);

CREATE INDEX idx_transaction_event_customer_time
    ON transaction_event (customer_id, transaction_time);

CREATE INDEX idx_transaction_event_received_at
    ON transaction_event (received_at);

--rollback DROP TABLE transaction_event;

--changeset odwambombo:002-create-fraud-assessment
CREATE TABLE fraud_assessment (
    id UUID NOT NULL,
    transaction_event_id UUID NOT NULL,
    risk_score INTEGER NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    flagged BOOLEAN NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_fraud_assessment PRIMARY KEY (id),
    CONSTRAINT uk_fraud_assessment_transaction_event UNIQUE (transaction_event_id),
    CONSTRAINT fk_fraud_assessment_transaction_event
        FOREIGN KEY (transaction_event_id)
        REFERENCES transaction_event (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_fraud_assessment_risk_score_non_negative CHECK (risk_score >= 0),
    CONSTRAINT ck_fraud_assessment_risk_level
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX idx_fraud_assessment_risk_level_evaluated_at
    ON fraud_assessment (risk_level, evaluated_at);

CREATE INDEX idx_fraud_assessment_flagged_evaluated_at
    ON fraud_assessment (flagged, evaluated_at);

CREATE INDEX idx_fraud_assessment_evaluated_at
    ON fraud_assessment (evaluated_at);

--rollback DROP TABLE fraud_assessment;

--changeset odwambombo:003-create-fraud-rule-result
CREATE TABLE fraud_rule_result (
    id UUID NOT NULL,
    fraud_assessment_id UUID NOT NULL,
    rule_code VARCHAR(100) NOT NULL,
    score INTEGER NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_fraud_rule_result PRIMARY KEY (id),
    CONSTRAINT uk_fraud_rule_result_assessment_rule
        UNIQUE (fraud_assessment_id, rule_code),
    CONSTRAINT fk_fraud_rule_result_assessment
        FOREIGN KEY (fraud_assessment_id)
        REFERENCES fraud_assessment (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_fraud_rule_result_score_non_negative CHECK (score >= 0)
);

--rollback DROP TABLE fraud_rule_result;
