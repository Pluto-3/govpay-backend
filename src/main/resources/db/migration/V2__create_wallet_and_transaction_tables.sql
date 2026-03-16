-- V2__create_wallet_and_transaction_tables.sql
-- Wallets table — one wallet per user, currency-aware

CREATE TABLE wallets (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance         BIGINT       NOT NULL DEFAULT 0,   -- stored in smallest unit (e.g. cents)
    currency        VARCHAR(3)   NOT NULL DEFAULT 'TZS',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,   -- for optimistic locking
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_wallets_balance_positive CHECK (balance >= 0),
    CONSTRAINT chk_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_status  ON wallets(status);

-- Transactions table — immutable audit log

CREATE TABLE transactions (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_wallet_id    UUID         REFERENCES wallets(id),
    recipient_wallet_id UUID         REFERENCES wallets(id),
    type                VARCHAR(30)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    amount              BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'TZS',
    description         VARCHAR(255),
    reference           VARCHAR(100) UNIQUE,  -- idempotency key / external ref
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    failed_reason       VARCHAR(500),

    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_type   CHECK (type IN (
        'TOP_UP', 'P2P_TRANSFER', 'UTILITY_PAYMENT', 'GOVERNMENT_FEE',
        'REFUND', 'REVERSAL', 'ADJUSTMENT'
    )),
    CONSTRAINT chk_transactions_status CHECK (status IN (
        'PENDING', 'COMPLETED', 'FAILED', 'REVERSED'
    ))
);

CREATE INDEX idx_transactions_sender        ON transactions(sender_wallet_id);
CREATE INDEX idx_transactions_recipient     ON transactions(recipient_wallet_id);
CREATE INDEX idx_transactions_status        ON transactions(status);
CREATE INDEX idx_transactions_type          ON transactions(type);
CREATE INDEX idx_transactions_created_at    ON transactions(created_at DESC);
CREATE INDEX idx_transactions_reference     ON transactions(reference) WHERE reference IS NOT NULL;

-- Auto-update updated_at on wallets

CREATE TRIGGER set_wallets_updated_at
    BEFORE UPDATE ON wallets
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();