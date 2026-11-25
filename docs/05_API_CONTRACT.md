# 05. API CONTRACT & SPECIFICATIONS

## 1. AI Agent Service API (Python / FastAPI)

### `POST /api/v1/ai/parse-expense`
Parses raw text or SMS body into a structured expense JSON DTO.

* **Headers:** `Content-Type: application/json`
* **Request Body:**
```json
{
  "raw_text": "Spent 450 rupees on lunch at Paradise Biryani",
  "currency": "INR"
}
```
* **Success Response (`200 OK`):**
```json
{
  "transaction_type": "EXPENSE",
  "amount": 450.00,
  "currency": "INR",
  "category": "Food & Dining",
  "merchant": "Paradise Biryani",
  "confidence_score": 0.98
}
```
`transaction_type` is `EXPENSE` for a completed debit, `INCOME` for a completed credit, or `NON_TRANSACTION` for a message that does not represent a completed transaction. For `NON_TRANSACTION`, amount, currency, category, and merchant are null. Completed transactions require a positive amount and supported category.
* **Error Response (`400 Bad Request`):**
```json
{
  "timestamp": "2026-11-11T14:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Raw text input cannot be empty."
}
```

---

## 2. Expense Service API (Java / Spring Boot)

### Authentication and financial ownership

Clients call financial APIs through the API gateway. A normal request supplies `Authorization: Bearer <token>`; the SMS webhook supplies its dedicated `X-Webhook-Token`. The Phase 1 gateway maps the accepted credential to a configured user ID, removes any caller-supplied identity header, and forwards `X-Authenticated-User` to the private expense service.

`X-Authenticated-User` is an internal trust header, not a client authentication mechanism. The expense service must not be exposed directly to untrusted callers, because a direct caller could otherwise forge it. The Phase 1 shared-token configuration represents one configured user per credential; a real multi-user deployment should replace it with verified per-user JWT or session identity.

Accounts, categories, transactions, dashboard totals, recent transactions, and budget-status queries are scoped to the authenticated user. Looking up another user's resource returns `404 Not Found` so the API does not reveal whether that resource exists.

### Account management

#### `POST /api/v1/accounts`

Creates an account. `openingBalance` initializes the current balance and `currency` must be a supported three-letter ISO 4217 code.

```json
{
  "accountName": "Primary Savings Account",
  "openingBalance": 50000.00,
  "currency": "INR"
}
```

The owner comes from the gateway-authenticated identity. Sending `userId` in the request body returns `400 Bad Request`.

Returns `201 Created` with the account representation.

#### `GET /api/v1/accounts`

Returns only accounts owned by the authenticated user.

#### `GET /api/v1/accounts/{id}`

Returns one account or `404 Not Found`.

#### `PUT /api/v1/accounts/{id}`

Updates the account name and currency. The current balance is ledger-controlled and cannot be overwritten by this endpoint, and ownership cannot be transferred through it. Sending `balance` or `userId` returns `400 Bad Request`. Currency can change only when the balance is zero and no transactions reference the account; otherwise the endpoint returns `409 Conflict`.

```json
{
  "accountName": "Travel Fund",
  "currency": "USD"
}
```

#### `DELETE /api/v1/accounts/{id}`

Deletes an account with `204 No Content`. An account with transaction history is retained and returns `409 Conflict` so ledger records are not removed accidentally.

Account responses use this shape:

```json
{
  "id": 1,
  "userId": "usr_abhishek",
  "accountName": "Primary Savings Account",
  "balance": 50000.00,
  "currency": "INR",
  "createdAt": "2026-11-25T12:00:00"
}
```

### Expense ingestion and dashboard

### `POST /api/v1/expenses/parse-and-save`
Takes raw user text, calls AI Agent Service internally, and saves transaction.

* **Request Body:**
```json
{
  "accountId": 1,
  "rawInput": "Spent 450 rupees on lunch at Paradise Biryani"
}
```

The current natural-language route is `POST /api/v1/expenses/parse`. If AI parsing is unavailable or returns unusable amount/category data, it returns `503 Service Unavailable` with `details.manualFallbackRequired` set to `"true"`; no transaction is saved. The caller can retry with explicit values:

```json
{
  "rawText": "Spent 450 rupees on lunch",
  "manualFallback": {
    "amount": 450.00,
    "categoryName": "Food & Dining",
    "merchant": "Lunch"
  }
}
```

When `manualFallback` is supplied, AI parsing is skipped. `amount` must be positive with at most two decimal places, and `categoryName` is required. The SMS webhook accepts the same optional object for a caller-assisted retry.
* **Success Response (`201 Created`):**
```json
{
  "transactionId": 101,
  "accountId": 1,
  "categoryName": "Food & Dining",
  "amount": 450.00,
  "merchant": "Paradise Biryani",
  "rawInput": "Spent 450 rupees on lunch at Paradise Biryani",
  "createdAt": "2026-11-11T14:02:00Z"
}
```

### `POST /api/v1/expenses/webhook/sms`
Automated SMS Webhook Receiver.

* **Request Body:**
```json
{
  "sender": "HDFCBK",
  "body": "Rs. 450.00 debited from A/c XX1234 on 11-Nov-26 at Swiggy. Info: UPI/4321."
}
```
The AI classifier maps completed debit SMS messages to `EXPENSE` and credits to `INCOME`. Balance-only notices, OTPs, offers, payment requests, and failed or pending messages are not saved and return `422 Unprocessable Entity` with a message that no transaction was recorded. A credit transaction increases the account balance; a debit decreases it.

If the AI service is unavailable, the caller may retry with the existing `manualFallback` fields and an optional `transactionType` of `EXPENSE` or `INCOME`. The omitted type defaults to `EXPENSE` for backward compatibility.
* **Success Response (`201 Created`):**
```json
{
  "status": "PROCESSED",
  "transactionId": 102,
  "parsedAmount": 450.00,
  "merchant": "Swiggy"
}
```

### `GET /api/v1/expenses/{transactionId}`

Returns one ledger transaction or `404 Not Found`.

### `PUT /api/v1/expenses/{transactionId}`

Replaces the editable transaction fields. The operation atomically reverses the old account-balance effect and applies the new one, including when the account or transaction type changes.

```json
{
  "accountId": 1,
  "categoryName": "Food & Dining",
  "amount": 500.00,
  "transactionType": "EXPENSE",
  "merchant": "Restaurant",
  "rawInput": "Corrected dinner transaction"
}
```

The amount must be positive with at most two decimal places. `transactionType` is optional and defaults to `EXPENSE`; when supplied it must be `EXPENSE` or `INCOME`.

### `DELETE /api/v1/expenses/{transactionId}`

Reverses the transaction's account-balance effect, deletes the ledger row, and returns `204 No Content`. Monthly budget totals update because they are calculated from current ledger rows.

### Balance concurrency rule

Any operation that can change an account balance obtains a PostgreSQL write lock on the account row inside the same database transaction. A concurrent operation on that account waits, then reads the committed balance before applying its own ledger effect. Transaction updates and deletes also lock the transaction row. When an update moves a transaction between accounts, both account rows are locked in ascending ID order to reduce deadlock risk.

Read-only account, expense, and dashboard requests do not request these write locks.

### `GET /api/v1/expenses/dashboard-summary`
Fetches account balance, spending ledgers, and budget progress.

* **Success Response (`200 OK`):**
```json
{
  "totalBalance": 49550.00,
  "totalSpentThisMonth": 450.00,
  "categoryBudgets": [
    {
      "categoryName": "Food & Dining",
      "limit": 10000.00,
      "spent": 450.00,
      "percentageUsed": 4.5
    }
  ],
  "recentTransactions": [
    {
      "id": 101,
      "amount": 450.00,
      "category": "Food & Dining",
      "merchant": "Paradise Biryani",
      "createdAt": "2026-11-11T14:02:00Z"
    }
  ]
}
```

### Category budget management

#### `POST /api/v1/categories`

Creates a category and monthly budget cap for the authenticated user. Names are unique without regard to case within that user's categories; two different users may use the same name. The limit must be nonnegative, and `0.00` means that no budget alert cap is active.

```json
{
  "name": "Education",
  "monthlyBudgetLimit": 3000.00
}
```

Returns `201 Created`. A duplicate name returns `409 Conflict`.

#### `GET /api/v1/categories`

Returns categories ordered by name.

#### `GET /api/v1/categories/{categoryId}`

Returns one category budget or `404 Not Found`.

#### `PUT /api/v1/categories/{categoryId}`

Replaces the category name and monthly limit using the same validation as creation.

#### `DELETE /api/v1/categories/{categoryId}`

Deletes an unused category and returns `204 No Content`. A category referenced by ledger transactions returns `409 Conflict` so transaction history remains intact.

Category management responses use this shape:

```json
{
  "id": 9,
  "name": "Education",
  "monthlyBudgetLimit": 3000.00
}
```

### `GET /api/v1/categories/{categoryId}/budget-status?month=YYYY-MM&throughTransactionId={id}`
Returns the category's monthly limit and expense total through the specified transaction. The Notification Service uses this after consuming an expense event so later transactions do not change that event's budget check.

* **Success Response (`200 OK`):**
```json
{
  "categoryId": 1,
  "categoryName": "Food & Dining",
  "monthlyLimit": 10000.00,
  "spent": 450.00
}
```

### `POST /api/v1/budget-alerts`
Records an over-budget alert once per expense event. The Notification Service calls this after checking the monthly total. Repeating the same `eventId` returns `created: false` and does not add another receipt.

* **Request Body:**
```json
{
  "eventId": "e98b7c3e-3f9b-4814-850d-6f00422db787",
  "categoryId": 1,
  "monthlyLimit": 10000.00,
  "spent": 10450.00
}
```
* **Success Response (`200 OK`):** `{"created": true}` on first insert, `{"created": false}` on replay.

---

## 3. RabbitMQ Message Event Contract

* **Exchange:** `expense.exchange` (Topic Exchange)
* **Routing Key:** `expense.created`
* **Queue:** `budget.alerts.queue`
* **Payload Structure:**
```json
{
  "eventId": "evt_987654321",
  "transactionId": 101,
  "userId": "usr_abhishek",
  "accountId": 1,
  "categoryId": 1,
  "categoryName": "Food & Dining",
  "amount": 450.00,
  "transactionType": "EXPENSE",
  "timestamp": "2026-11-11T14:02:00Z"
}
```

The Expense Service writes the event to `expense_event_outbox` in the same PostgreSQL transaction as the expense. A scheduled publisher sends pending rows to RabbitMQ and marks them published only after broker confirmation. Publication is at least once; the alert receipt keyed by `eventId` prevents a replay from creating a second durable alert. The Notification Service retries failed budget lookups with a delay and requeues the message until the dependency recovers.
