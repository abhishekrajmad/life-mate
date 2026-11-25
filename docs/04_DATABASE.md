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
  | user_id          |    |    | user_id            |    |    | account_id (FK)     |
  | account_name     |    |    | name               |    |    | category_id (FK)    |
  | balance          |    +----| monthly_budget_lim |    +----| amount              |
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

### Ownership migration

`V6__financial_data_ownership.sql` adds `categories.user_id`, assigns existing seeded categories to the Phase 1 owner `usr_abhishek`, and replaces global category-name uniqueness with a case-insensitive unique index on `(user_id, LOWER(name))`. It also indexes account and category owner columns.

Transactions inherit ownership through their account. Service queries require both the resource identifier and authenticated account owner, while category resolution separately requires the same owner. This keeps one user's accounts, categories, ledger rows, dashboard aggregates, and budget calculations isolated from another user's data.

### Balance concurrency control

Balance-changing service transactions use PostgreSQL pessimistic row locks. Account repository methods annotated with `PESSIMISTIC_WRITE` produce `SELECT ... FOR NO KEY UPDATE`, so only one writer at a time can calculate a new balance from a given account row.

Transaction replacement locks the ledger row and all affected account rows. Multiple accounts are requested in ascending ID order to keep lock acquisition consistent. Read-only queries use the ordinary repository methods and do not take these write locks. This is transaction behavior rather than a schema change, so it requires no additional Flyway migration.

## 4. Redis Key Structure
* Gateway rate-limit keys use the authenticated user ID, with direct remote IP as the fallback when no identity is available.
* `budget_summary:{user_id}:{month}:` Cached total monthly spending calculation.
