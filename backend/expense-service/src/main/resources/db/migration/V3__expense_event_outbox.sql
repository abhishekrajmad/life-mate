CREATE TABLE expense_event_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX idx_expense_event_outbox_pending
    ON expense_event_outbox (created_at, event_id)
    WHERE published_at IS NULL;
