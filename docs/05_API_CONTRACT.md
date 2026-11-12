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
  "amount": 450.00,
  "currency": "INR",
  "category": "Food & Dining",
  "merchant": "Paradise Biryani",
  "confidence_score": 0.98
}
```
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

### `POST /api/v1/expenses/parse-and-save`
Takes raw user text, calls AI Agent Service internally, and saves transaction.

* **Request Body:**
```json
{
  "accountId": 1,
  "rawInput": "Spent 450 rupees on lunch at Paradise Biryani"
}
```
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
* **Success Response (`201 Created`):**
```json
{
  "status": "PROCESSED",
  "transactionId": 102,
  "parsedAmount": 450.00,
  "merchant": "Swiggy"
}
```

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

---

## 3. RabbitMQ Message Event Contract

* **Exchange:** `expense.exchange` (Topic Exchange)
* **Routing Key:** `expense.created`
* **Queue:** `budget.alerts.queue`
* **Payload Structure:**
```json
{
  "eventId": "evt_987654321",
  "userId": "usr_abhishek",
  "accountId": 1,
  "categoryId": 1,
  "categoryName": "Food & Dining",
  "amount": 450.00,
  "timestamp": "2026-11-11T14:02:00Z"
}
```