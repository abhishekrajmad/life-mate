CREATE TABLE budget_alert_receipts (
    event_id VARCHAR(36) PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    monthly_limit DECIMAL(12, 2) NOT NULL,
    spent DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
