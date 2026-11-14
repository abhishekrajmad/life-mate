CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'INR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    monthly_budget_limit DECIMAL(12, 2) DEFAULT 0.00
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(id),
    category_id BIGINT REFERENCES categories(id),
    amount DECIMAL(12, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL DEFAULT 'EXPENSE',
    merchant VARCHAR(100),
    raw_input TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);