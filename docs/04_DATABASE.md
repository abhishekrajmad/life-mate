# 04. DATABASE DESIGN

## 1. Database-per-Service Paradigm
* **Expense Service:** PostgreSQL 15 (Relational storage for accounts, categories, and transactions).
* **API Gateway:** Redis (In-memory storage for rate limits).
* **Fitness Service (Phase 2):** MongoDB Atlas (Document storage for flexible health logs).

## 2. PostgreSQL Relational ER Schema

```text
  +------------------+         +--------------------+         +---------------------+
  |     ACCOUNTS     |         |    CATEGORIES      |         |    TRANSACTIONS     |
  +------------------+         +--------------------+         +---------------------+
  | id (PK)          |<---+    | id (PK)            |<---+    | id (PK)             |
  | user_id          |    |    | name               |    |    | account_id (FK)     |
  | account_name     |    +----| monthly_budget_lim |    +----| category_id (FK)    |
  | balance          |         +--------------------+         | amount              |
  | currency         |                                        | transaction_type    |
  | created_at       |                                        | merchant            |
  +------------------+                                        | raw_input           |
                                                              | created_at          |
                                                              +---------------------+
```

## 3. Flyway Migration Scripts

### `src/main/resources/db/migration/V1__init_expense_schema.sql`
```sql
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
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount DECIMAL(12, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL DEFAULT 'EXPENSE',
    merchant VARCHAR(100),
    raw_input TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_category ON transactions(category_id);
CREATE INDEX idx_transactions_created ON transactions(created_at);

-- Initial Seed Data
INSERT INTO categories (name, monthly_budget_limit) VALUES 
('Food & Dining', 10000.00),
('Transportation', 3000.00),
('Shopping', 5000.00),
('Bills & Utilities', 8000.00),
('Health & Fitness', 4000.00),
('General', 2000.00);

INSERT INTO accounts (user_id, account_name, balance, currency) VALUES
('usr_abhishek', 'Primary Salary Account', 50000.00, 'INR');
```

## 4. Redis Key Structure
* `rate_limit:{user_id}:` Key tracking API request counts per minute.
* `budget_summary:{user_id}:{month}:` Cached total monthly spending calculation.