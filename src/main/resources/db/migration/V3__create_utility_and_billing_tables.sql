-- V3__create_utility_and_billing_tables.sql
-- Utility services (water, electricity, taxes, etc.)

CREATE TABLE utility_services (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    code            VARCHAR(20)  NOT NULL UNIQUE,
    type            VARCHAR(30)  NOT NULL,
    provider_name   VARCHAR(100) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_utility_type CHECK (type IN (
        'WATER', 'ELECTRICITY', 'TAX', 'FINE', 'GOVERNMENT_FEE', 'OTHER'
    ))
);

CREATE INDEX idx_utility_services_code ON utility_services(code);
CREATE INDEX idx_utility_services_type ON utility_services(type);

-- Bills — generated for users per utility service

CREATE TABLE bills (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id),
    utility_service_id  UUID         NOT NULL REFERENCES utility_services(id),
    amount              BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'TZS',
    status              VARCHAR(20)  NOT NULL DEFAULT 'UNPAID',
    due_date            TIMESTAMPTZ  NOT NULL,
    description         VARCHAR(500),
    transaction_id      UUID         REFERENCES transactions(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    paid_at             TIMESTAMPTZ,

    CONSTRAINT chk_bills_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_bills_status CHECK (status IN ('UNPAID', 'PAID', 'OVERDUE', 'CANCELLED'))
);

CREATE INDEX idx_bills_user_id     ON bills(user_id);
CREATE INDEX idx_bills_status      ON bills(status);
CREATE INDEX idx_bills_due_date    ON bills(due_date);

-- Seed a few utility services for dev/demo

INSERT INTO utility_services (id, name, code, type, provider_name) VALUES
    (gen_random_uuid(), 'DAWASCO Water',          'DAWASCO',   'WATER',          'Dar es Salaam Water and Sewerage Corporation'),
    (gen_random_uuid(), 'TANESCO Electricity',    'TANESCO',   'ELECTRICITY',    'Tanzania Electric Supply Company'),
    (gen_random_uuid(), 'TRA Income Tax',         'TRA_ITAX',  'TAX',            'Tanzania Revenue Authority'),
    (gen_random_uuid(), 'Traffic Fine',           'TRAFINE',   'FINE',           'Tanzania Police Force'),
    (gen_random_uuid(), 'Business License Fee',   'BIZLIC',    'GOVERNMENT_FEE', 'Ministry of Trade');