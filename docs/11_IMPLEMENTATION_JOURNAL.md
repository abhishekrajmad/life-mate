# Life Mate Implementation Journal

Last updated: 2026-11-27

This private journal follows `REMAINING_FIXES.md` in the same order. Every completed checklist item has its own entry. The checklist number and bold fix name are copied into the journal heading so it is clear which work belongs to which fix.

Use this journal for debugging, resuming work, and interview review. Never add real passwords, access tokens, API keys, bank messages, or personal financial data.

## How entries are organized

Each fix uses the same structure:

1. **Checklist item** states the outcome tracked in `REMAINING_FIXES.md`.
2. **Why this fix was needed** explains the original risk or missing behavior.
3. **What changed** records the implementation and design decisions.
4. **Resulting flow** shows how requests, data, or events move now.
5. **Verification** records only tests and live checks that actually ran.
6. **Known limitations** separates remaining work from completed work.
7. **Interview review** gives a short explanation of the engineering decision.

Do not merge checklist items into one journal entry. Add new entries after the previous fix so the journal remains chronological and matches the checklist.

## Fix index

| Fix | Name | Status |
| --- | --- | --- |
| 1.1 | End-to-end event delivery verification | Complete |
| 1.2 | Transactional outbox reliability | Complete |
| 1.3 | Duplicate alert suppression | Complete |
| 1.4 | Notification dependency retries | Complete |
| 2.1 | Gateway service routing | Complete |
| 2.2 | Gateway CORS and rate limiting | Complete |
| 2.3 | Frontend gateway connection | Complete |
| 2.4 | Gateway and webhook authentication | Complete |
| 3.1 | Account CRUD APIs | Complete |
| 3.2 | Category budget cap CRUD APIs | Complete |
| 3.3 | Transaction ledger update and delete | Complete |
| 3.4 | Financial data ownership | Complete |
| 3.5 | Concurrent balance protection | Complete |
| 4.1 | Manual categorization fallback | Complete |
| 4.2 | SMS transaction classification | Complete |
| 4.4 | AI output and currency validation | Complete |
| 4.5 | Sensitive transaction logging | Complete |
| 5.1 | Dashboard UI states | Implemented; build and targeted lint pass |
| 5.2 | React effect lint fix | Complete |
| 5.3 | Ledger transaction details | Complete |
| 8.2 | Environment examples and startup configuration | Complete |

## Current system map

```text
React frontend or phone automation
                |
                v
       API gateway on port 8080
       - routing
       - CORS
       - Redis rate limiting
       - Phase 1 credential-to-user mapping
       - trusted identity injection
          |                         |
          v                         v
Expense service on 8081      AI service on 8000
          |
          +---- PostgreSQL transaction and outbox
          |
          v
       RabbitMQ
          |
          v
Notification service
          |
          +---- budget lookup and durable alert receipt
```

## Fix 1.1 End-to-end event delivery verification

Status: complete and verified on 2026-11-24.

Checklist item: Verify transaction to commit to RabbitMQ to notification with an isolated PostgreSQL database and a temporary RabbitMQ queue.

### Why this fix was needed

Unit tests can show that individual classes behave correctly, but they do not prove that database commits, RabbitMQ routing, JSON conversion, queue bindings, and the notification listener work together. A configuration mismatch could leave every component passing its own tests while the real event never reaches the consumer.

### What was done

An isolated PostgreSQL database and a temporary RabbitMQ queue were used to exercise the real pipeline without depending on existing project data or consuming existing messages.

An expense that crossed its category budget was saved. The database transaction committed, its event was published through RabbitMQ, the notification service consumed it, and the service logged the over-budget alert.

### Resulting flow verified

```text
Create expense
    |
    v
Commit transaction and event intent in PostgreSQL
    |
    v
Publish event to RabbitMQ exchange
    |
    v
Route event to temporary queue
    |
    v
Notification listener checks budget
    |
    v
Over-budget alert is logged
```

### Verification

- The expense transaction committed in the isolated database.
- RabbitMQ delivered the event to the temporary queue.
- The notification service logged the expected over-budget alert.

### Known limitations

This check proved the event path under the tested local conditions. Full application end-to-end testing with the frontend, AI service, gateway, and all normal queues remains a later checklist item.

### Interview review

Question: Why run a live pipeline check when unit tests already exist?

Answer: Unit tests isolate code, while an end-to-end integration check proves that real configuration boundaries work together, including database transactions, serialization, exchange routing, queue binding, and consumer startup.

## Fix 1.2 Transactional outbox reliability

Status: complete and verified on 2026-11-24.

Checklist item: Store every expense event with the expense, retry failed publication, and mark it sent only after broker confirmation.

### Why this fix was needed

Saving an expense and publishing to RabbitMQ are two separate writes. If PostgreSQL commits and RabbitMQ fails, the financial transaction exists but its budget notification is lost. Publishing first creates the reverse risk: a consumer can receive an event for a database transaction that later rolls back.

### What changed

The expense service now uses the transactional outbox pattern:

1. `ExpenseService.createExpense` updates the balance and saves the transaction.
2. It creates an `ExpenseCreatedEvent` with a unique event ID.
3. `ExpenseOutboxWriter` serializes the event into `expense_event_outbox` inside the same PostgreSQL transaction.
4. `ExpenseOutboxPublisher` polls unpublished rows separately.
5. It publishes through RabbitMQ and waits for broker confirmation.
6. It sets `published_at` only after confirmation.
7. A failed publication leaves the row pending for a later polling cycle.

The key guarantee is that the expense and its event intent commit or roll back together. RabbitMQ availability no longer controls whether the API can durably record the intent to publish.

### Resulting flow

```text
One PostgreSQL transaction
    +---- update account balance
    +---- insert ledger transaction
    +---- insert outbox event
              |
              v
Scheduled publisher reads pending event
              |
              v
RabbitMQ confirms publication
              |
              v
Set published_at
```

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/messaging/ExpenseOutboxWriter.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/messaging/ExpenseOutboxPublisher.java`
- `backend/expense-service/src/main/resources/db/migration/V3__expense_event_outbox.sql`
- `backend/expense-service/src/test/java/com/lifemate/expense/messaging/ExpenseOutboxPublisherTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceEventTest.java`

### Verification

- A unit test confirms the expense service writes an event for every saved expense.
- A broker-failure test confirms the outbox row stays pending.
- A broker-confirmation test confirms the row is marked published.
- The live pipeline in Fix 1.1 confirmed successful publication.

Regression command:

```bash
cd backend/expense-service
mvn test
```

### Known limitations

The outbox provides at-least-once publication, not exactly-once delivery. A crash after RabbitMQ accepts an event but before `published_at` commits can cause the event to be sent again. Consumers must be idempotent.

### Interview review

Question: Why not publish directly to RabbitMQ inside the expense method?

Answer: PostgreSQL and RabbitMQ do not share one local transaction. The outbox stores business data and event intent atomically, then makes publication asynchronous and retryable.

## Fix 1.3 Duplicate alert suppression

Status: complete and verified on 2026-11-24.

Checklist item: Prevent duplicate alert records with a durable receipt keyed by event ID.

### Why this fix was needed

At-least-once delivery means the same RabbitMQ message can arrive more than once. Without duplicate protection, one expense could create or log the same budget alert repeatedly.

### What changed

Flyway migration `V4__budget_alert_receipts.sql` creates `budget_alert_receipts` with `event_id` as its primary key.

When spending exceeds the limit, `BudgetAlertListener` asks the expense service to record a receipt. `BudgetAlertReceiptService` uses:

```sql
INSERT INTO budget_alert_receipts (...)
VALUES (...)
ON CONFLICT (event_id) DO NOTHING
```

The first delivery returns `created: true` and logs the alert. Replaying the same event returns `created: false`, so the listener stays quiet.

The budget query also calculates spending through the triggering transaction ID. Later transactions do not change what an older event should have observed.

### Resulting flow

```text
Expense event exceeds budget
    |
    v
Insert receipt with event_id
    |
    +---- inserted: return created true and log alert
    |
    +---- conflict: return created false and do not log again
```

### Main implementation areas

- `backend/expense-service/src/main/resources/db/migration/V4__budget_alert_receipts.sql`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/BudgetAlertReceiptService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/controller/BudgetAlertReceiptController.java`
- `backend/notification-service/src/main/java/com/lifemate/notification/listener/BudgetAlertListener.java`
- `backend/notification-service/src/test/java/com/lifemate/notification/listener/BudgetAlertListenerTest.java`

### Verification

- A live duplicate receipt request returned `created: false`.
- The replay test confirms an already-recorded event does not log another alert.
- The database primary key makes suppression durable across restarts and safe across multiple service instances.

Regression command:

```bash
cd backend/notification-service
mvn test
```

### Known limitations

This receipt protects budget alerts. It does not yet make the incoming SMS webhook idempotent; repeating a valid webhook call can still create duplicate transactions.

### Interview review

Question: Why use a database receipt instead of an in-memory set?

Answer: Memory disappears after restart and is not shared across replicas. A database uniqueness constraint gives durable and atomic duplicate suppression.

## Fix 1.4 Notification dependency retries

Status: complete and verified on 2026-11-24.

Checklist item: Bound notification budget lookup time and retry transient failures without losing the RabbitMQ message.

### Why this fix was needed

The notification listener depends on the expense service for budget totals and alert receipts. An unbounded HTTP call could hang a listener thread. A temporary expense-service outage could also cause a message to be rejected or lost before the dependency recovers.

### What changed

`ExpenseServiceClient` now uses configurable connection and read timeouts. `RabbitMQConsumerConfig` applies delayed retry attempts to listener failures. When one retry cycle is exhausted, `ImmediateRequeueMessageRecoverer` returns the message to RabbitMQ so another cycle can run later.

Configuration includes:

- `EXPENSE_SERVICE_CONNECT_TIMEOUT_MS` with a 2000 ms default;
- `EXPENSE_SERVICE_READ_TIMEOUT_MS` with a 5000 ms default;
- `BUDGET_ALERT_RETRY_ATTEMPTS` with a default of 3;
- `BUDGET_ALERT_RETRY_DELAY_MS` with a 2000 ms default.

### Resulting flow

```text
Budget lookup fails
    |
    v
Retry with fixed delay
    |
    +---- dependency recovers: process message
    |
    +---- retry cycle exhausted: requeue message
                                      |
                                      v
                              later retry cycle
```

### Main implementation areas

- `backend/notification-service/src/main/java/com/lifemate/notification/client/ExpenseServiceClient.java`
- `backend/notification-service/src/main/java/com/lifemate/notification/config/RabbitMQConsumerConfig.java`
- `backend/notification-service/src/main/resources/application.yml`

### Verification

A prolonged dependency outage was tested. The message continued retrying and was processed after the dependency returned.

### Known limitations

A permanently invalid poison message can be requeued indefinitely. Production operation still needs monitoring, retry metrics, and eventually a dead-letter queue or parking-lot strategy.

### Interview review

Question: Why combine timeouts, retries, and requeue?

Answer: Timeouts bound each failed attempt, delayed retries absorb short outages, and requeue preserves the message when the outage lasts longer than one retry cycle.

## Fix 2.1 Gateway service routing

Status: complete and committed on 2026-11-25 in commit `f8eca15`. The account route was added with Fix 3.1.

Checklist item: Create the API gateway and route public expense, dashboard, category, account, and AI APIs while excluding the internal alert receipt endpoint.

### Why this fix was needed

Without a gateway, clients must know the address of every backend service and cross-cutting controls must be repeated in each service. A gateway provides one stable client entry point.

### What changed

`backend/api-gateway` was created with Spring Cloud Gateway. Path predicates forward:

| Public path | Upstream |
| --- | --- |
| `/api/v1/expenses` and `/api/v1/expenses/**` | Expense service |
| `/api/v1/accounts` and `/api/v1/accounts/**` | Expense service |
| `/api/v1/dashboard/**` | Expense service |
| `/api/v1/categories/**` | Expense service |
| `/api/v1/ai/**` | AI service |

The internal `/api/v1/budget-alerts` receipt endpoint is deliberately excluded. The notification service calls it directly on the private service network.

Upstream locations are configurable with `EXPENSE_SERVICE_URL` and `AI_AGENT_URL`.

### Resulting flow

```text
Client calls one gateway address
    |
    v
Gateway matches request path
    |
    +---- financial paths to expense service
    |
    +---- AI paths to AI service
```

### Main implementation areas

- `backend/api-gateway/pom.xml`
- `backend/api-gateway/src/main/java/com/lifemate/gateway/ApiGatewayApplication.java`
- `backend/api-gateway/src/main/resources/application.yml`
- `backend/api-gateway/README.md`

### Verification

Live route checks confirmed that configured public paths reached the expected upstream services. The internal alert receipt path was not exposed through the gateway.

### Known limitations

The upstream services must remain private. If the expense or AI service is exposed directly, callers can bypass gateway controls.

### Interview review

Question: Why use an API gateway?

Answer: It gives clients one entry point and centralizes cross-cutting behavior such as routing, CORS, rate limiting, and authentication while business logic stays in the owning service.

## Fix 2.2 Gateway CORS and rate limiting

Status: complete and verified on 2026-11-25.

Checklist item: Add gateway CORS and Redis-backed rate limiting.

### Why this fix was needed

The browser needs an explicit origin policy, and public APIs need basic protection from accidental loops or request floods. CORS and rate limiting solve different problems and both belong at the gateway boundary.

### What changed

Gateway CORS configuration allowlists the configured frontend origins and allowed HTTP methods. Redis-backed `RequestRateLimiter` applies a token bucket to routed requests:

- `GATEWAY_RATE_REPLENISH` controls tokens added per second and defaults to 10;
- `GATEWAY_RATE_BURST` controls maximum burst capacity and defaults to 20;
- requests without an available token receive HTTP 429.

`ClientKeyResolverConfig` uses the direct remote IP as the rate-limit key. Forwarded IP headers are intentionally ignored because callers can forge them unless a trusted reverse proxy is configured.

### Resulting flow

```text
Browser request
    |
    +---- origin not allowed: browser CORS access is rejected
    |
    +---- origin allowed: rate-limit bucket is checked in Redis
                              |
                              +---- token available: route request
                              +---- no token: HTTP 429
```

### Main implementation areas

- `backend/api-gateway/src/main/resources/application.yml`
- `backend/api-gateway/src/main/java/com/lifemate/gateway/ClientKeyResolverConfig.java`
- `backend/api-gateway/README.md`

### Verification

Live checks confirmed:

- an allowed origin received the configured CORS behavior;
- a rejected origin was not allowed;
- requests under the limit were forwarded;
- requests over the limit received HTTP 429.

### Known limitations

Redis must be available for rate-limited routed requests. A deployment behind a reverse proxy needs a trusted-proxy design before forwarded client addresses can safely replace the direct proxy address.

### Interview review

Question: Is CORS authentication?

Answer: No. CORS is a browser policy controlling which web origins can read responses. Curl and server clients do not rely on it. Authentication separately verifies a caller credential.

Question: How does the rate limiter work?

Answer: Redis stores a token bucket per direct client IP. Tokens refill at a configured rate, bursts are capped, and an empty bucket returns HTTP 429.

## Fix 2.3 Frontend gateway connection

Status: complete on 2026-11-25.

Checklist item: Route Vite development API calls through the gateway and make the deployed API base configurable.

### Why this fix was needed

Adding a gateway has no effect if the frontend continues calling the expense service directly. Local development and deployed builds also need different ways to resolve the gateway URL.

### What changed

`frontend/vite.config.js` proxies `/api` to `http://127.0.0.1:8080` by default. `API_GATEWAY_PROXY_TARGET` can override the development target.

`frontend/src/App.jsx` now selects:

```text
VITE_API_BASE_URL when provided
otherwise /api/v1
```

The relative default works through Vite's development proxy and can also work behind a same-origin deployment proxy.

### Main implementation areas

- `frontend/vite.config.js`
- `frontend/src/App.jsx`

### Verification

Configuration inspection confirmed that `/api` requests target the gateway and that deployed builds can provide a gateway base URL.

### Known limitations

The frontend does not yet obtain or send the Phase 1 Bearer credential. Authentication-enabled browser deployment needs a safe credential flow; a shared secret must not be embedded in a public Vite bundle.

### Interview review

Question: Why use a Vite proxy locally?

Answer: The browser calls a same-origin relative URL while Vite forwards it to the gateway. This avoids hardcoding development service addresses in application code and simplifies local CORS behavior.

## Fix 2.4 Gateway and webhook authentication

Status: complete and verified on 2026-11-25.

Checklist item: Authenticate normal API routes and validate SMS webhook callers before the gateway can be exposed publicly.

### Why this fix was needed

Without authentication, anyone who reaches the gateway could read financial data or create transactions. Phone automation also needs a limited credential that does not grant access to the entire API.

### What changed

The Phase 1 design uses two distinct shared credentials:

| Route type | Credential |
| --- | --- |
| Normal API request | `Authorization: Bearer <GATEWAY_API_TOKEN>` |
| SMS webhook | `X-Webhook-Token: <GATEWAY_WEBHOOK_TOKEN>` |

`GatewayAuthenticationFilter` runs before routing. It:

- lets CORS preflight `OPTIONS` requests continue;
- checks the correct token for the requested route;
- uses constant-time byte comparison;
- returns HTTP 401 when validation fails;
- removes both credential headers before forwarding accepted requests.

`PublicExposureSecurityValidator` fails application startup when a non-loopback address is used without authentication. Enabled authentication requires two distinct, nonblank tokens of at least 32 characters.

Authentication remains disabled by default only for loopback local development.

### Resulting flow

```text
API request
    |
    +---- SMS webhook: validate dedicated webhook token
    |
    +---- other API path: validate Bearer token
                         |
                         +---- invalid: HTTP 401
                         +---- valid: remove credential header and route
```

### Main implementation areas

- `backend/api-gateway/src/main/java/com/lifemate/gateway/GatewayAuthenticationFilter.java`
- `backend/api-gateway/src/main/java/com/lifemate/gateway/GatewaySecurityProperties.java`
- `backend/api-gateway/src/main/java/com/lifemate/gateway/PublicExposureSecurityValidator.java`
- `backend/api-gateway/src/test/java/com/lifemate/gateway/GatewayAuthenticationFilterTest.java`
- `backend/api-gateway/src/test/java/com/lifemate/gateway/PublicExposureSecurityValidatorTest.java`
- `backend/api-gateway/src/main/resources/application.yml`
- `backend/api-gateway/README.md`

### Verification

```bash
cd backend/api-gateway
mvn test
```

Eight tests passed with no failures or errors. They cover valid and missing Bearer tokens, webhook credential isolation, valid webhook forwarding, CORS preflight, disabled loopback authentication, rejected unauthenticated public binding, and strong distinct-token validation.

Manual checks use authentication-enabled startup followed by requests with no token, the Bearer token, and the webhook token. No token returns HTTP 401; each valid token is accepted only for its intended route type.

### Known limitations

- Shared tokens do not identify individual users or provide login, expiry, refresh, roles, or revocation.
- The frontend does not yet acquire or send a Bearer token.
- The gateway does not solve per-user data ownership.
- Upstream services still require network isolation.
- Webhook caller authentication is complete, but webhook duplicate transaction prevention remains pending.

### Interview review

Question: Why use a separate webhook token?

Answer: It applies least privilege. A credential stored in phone automation can submit the webhook without gaining permission to read dashboards or call normal APIs.

Question: Why remove credentials before forwarding?

Answer: Internal services do not need these shared secrets. Removing them reduces exposure through upstream logs, errors, and additional forwarding.

Question: Is this sufficient for a production multi-user system?

Answer: No. It is a deliberately simple Phase 1 boundary. Multi-user support needs per-user identity such as validated JWT subjects plus authorization in the expense service.

## Fix 3.1 Account CRUD APIs

Status: complete and verified on 2026-11-25.

Checklist item: Add account create, read, update, and delete APIs with DTO validation and explicit currency and balance behavior.

### Why this fix was needed

Account data existed in PostgreSQL, but only the seeded account could be used because there was no management API. Currency and balance behavior were implicit. A careless update endpoint could overwrite the balance independently of the ledger or reinterpret historical values under a different currency.

### What changed

The expense service now provides:

| Method and path | Behavior |
| --- | --- |
| `POST /api/v1/accounts` | Create an account from user ID, name, opening balance, and currency. |
| `GET /api/v1/accounts` | List accounts. |
| `GET /api/v1/accounts/{id}` | Return one account or HTTP 404. |
| `PUT /api/v1/accounts/{id}` | Update the name and safely change currency. |
| `DELETE /api/v1/accounts/{id}` | Delete only when no transaction references the account. |

The gateway account path was added to Fix 2.1's route configuration.

Request DTOs validate required text, database lengths, two-decimal monetary precision, and three-letter currency format. Java's ISO 4217 `Currency` registry rejects invented codes and normalizes valid codes to uppercase.

### Balance decision

`openingBalance` is accepted during creation. Normal account update rejects a supplied `balance` because current balance is ledger-controlled:

```text
current balance = opening balance + income - expenses
```

Direct assignment would make the account disagree with transaction history. Opening balance is signed because the current schema does not model whether an account represents cash, overdraft, or debt.

### Currency and deletion decisions

Currency can change only when the balance is zero and no transactions reference the account. Otherwise the service returns HTTP 409 Conflict.

Deletion also checks for transactions and returns HTTP 409 when history exists. The service preserves the ledger rather than cascading destructive deletion or exposing a database error.

`PUT` rejects `userId`, preventing ordinary updates from transferring ownership. Full ownership authorization remains a separate fix.

### Resulting flow

```text
Client
  |
  v
Gateway account route
  |
  v
AccountController validates DTO
  |
  v
AccountService applies currency, balance, ownership, and deletion rules
  |
  v
AccountRepository and TransactionRepository
  |
  v
PostgreSQL
```

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/controller/AccountController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/AccountService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/AccountCreateRequestDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/AccountUpdateRequestDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/AccountResponseDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/exception/ResourceConflictException.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/exception/GlobalExceptionHandler.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/repository/TransactionRepository.java`
- `backend/api-gateway/src/main/resources/application.yml`
- `docs/05_API_CONTRACT.md`

### Verification

```bash
cd backend/expense-service
mvn test

cd ../api-gateway
mvn test
```

Results:

- 16 expense-service tests passed with no failures or errors;
- 8 gateway tests passed with no failures or errors;
- account tests cover DTO validation, normalization, unsupported currency, list and lookup, not-found responses, currency-change conflicts, protected balance and ownership fields, deletion conflicts, and successful deletion.

The expense-service test log includes an expected `broker unavailable` stack trace from the existing test that deliberately simulates outbox publication failure.

Live PostgreSQL verification used the updated service on temporary port 18081:

1. Create returned HTTP 201 and normalized `inr` to `INR`.
2. Update returned HTTP 200 and safely changed the empty account to `USD`.
3. Read returned the updated account with HTTP 200.
4. Delete returned HTTP 204.
5. A final read returned HTTP 404.

The temporary account was deleted and the temporary service process was stopped.

### Known limitations

- Account list and lookup are not scoped to an authenticated owner.
- Create still trusts `userId` from the caller.
- Concurrent expense writes can still lose account balance updates.
- The frontend has no account-management controls.
- Account types and type-specific balance rules are not modeled.

### Interview review

Question: Why is balance excluded from update?

Answer: Balance is ledger state. Changing it independently would break reconciliation. Adjustments should be represented as auditable ledger operations.

Question: Why return HTTP 409 for delete or currency conflicts?

Answer: The request shape is valid, but it conflicts with current resource state. HTTP 409 distinguishes that from malformed input and preserves financial history.

Question: Why validate currency in both the DTO and service?

Answer: DTO validation gives a clear format error. Service validation confirms that the three letters are an assigned ISO 4217 code and normalizes the value.

## Fix 3.2 Category budget cap CRUD APIs

Status: complete and verified on 2026-11-25.

Checklist item: Add category budget cap create, read, update, and delete APIs with nonnegative-limit validation while preserving the notification budget-status endpoint.

### Why this fix was needed

Categories and seeded monthly limits existed in PostgreSQL, but the only category API calculated read-only budget status for the notification service. Users and future frontend controls could not create categories, rename them, or change their monthly caps.

Category deletion also needed an explicit rule. Allowing a category with ledger history to disappear would either violate the foreign key or remove the meaning of old transactions.

### What changed

The expense service now provides:

| Method and path | Behavior |
| --- | --- |
| `POST /api/v1/categories` | Create a category and monthly limit. |
| `GET /api/v1/categories` | List categories ordered by name. |
| `GET /api/v1/categories/{id}` | Return one category or HTTP 404. |
| `PUT /api/v1/categories/{id}` | Replace the category name and limit. |
| `DELETE /api/v1/categories/{id}` | Delete only when no transaction references the category. |
| `GET /api/v1/categories/{id}/budget-status` | Existing notification calculation remains available. |

`CategoryBudgetRequestDTO` requires:

- a nonblank name of at most 50 characters;
- a nonnull monthly limit;
- a limit greater than or equal to zero;
- at most 10 integer digits and 2 fractional digits, matching `DECIMAL(12,2)`.

Names are trimmed and checked case-insensitively. The service produces a clear HTTP 409 conflict, while Flyway migration V5 adds a unique index on `LOWER(name)` so concurrent requests cannot bypass the check.

### Budget limit decision

A positive value is an active monthly alert cap. `0.00` remains valid and means that no budget alert cap is active. This preserves the existing notification condition, which checks alerts only when the limit is greater than zero.

Negative limits return HTTP 400. The API does not reinterpret a negative value as unlimited because that would make the stored financial rule ambiguous.

### Deletion decision

Before deletion, the service checks `TransactionRepository.existsByCategory_Id`. A referenced category returns HTTP 409 Conflict and remains available to describe its ledger transactions. An unused category returns HTTP 204 after deletion.

The existing global database-integrity handler remains a second line of defense if another foreign-key reference or a concurrent write creates a constraint conflict.

### Resulting flow

```text
Client category request
    |
    v
CategoryBudgetController validates DTO
    |
    v
CategoryBudgetService
    +---- normalize and check unique name
    +---- apply nonnegative budget rule
    +---- protect referenced categories from deletion
    |
    v
CategoryRepository and TransactionRepository
    |
    v
PostgreSQL with case-insensitive unique-name index
```

Notification processing continues to call the same budget-status service method and query spending through the triggering transaction ID.

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/controller/CategoryBudgetController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/CategoryBudgetService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/CategoryBudgetRequestDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/CategoryBudgetResponseDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/repository/CategoryRepository.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/repository/TransactionRepository.java`
- `backend/expense-service/src/main/resources/db/migration/V5__category_name_case_insensitive_unique.sql`
- `backend/expense-service/src/test/java/com/lifemate/expense/controller/CategoryBudgetControllerTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/CategoryBudgetServiceTest.java`
- `docs/05_API_CONTRACT.md`

### Verification

```bash
cd backend/expense-service
mvn test
```

Twenty-eight expense-service tests passed with no failures or errors. Nine category service tests and three category controller tests cover creation, trimming, case-insensitive duplicates, sorted listing, missing categories, updates, duplicate renames, budget-status compatibility, protected deletion, successful deletion, and invalid DTOs.

The `broker unavailable` stack trace in the suite output is expected from the existing outbox failure test.

Live PostgreSQL verification used the updated service on temporary port 18081:

1. Flyway validated five migrations and applied V5 successfully.
2. Creating a temporary category returned HTTP 201.
3. Creating the same name with different letter casing returned HTTP 409.
4. Updating its name and limit to `0.00` returned HTTP 200.
5. Reading it returned the updated values with HTTP 200.
6. Deleting it returned HTTP 204.
7. Reading it afterward returned HTTP 404.

The temporary category was deleted and the temporary service process was stopped.

### Known limitations

- Categories remain global because the schema has no category owner. Ownership is Fix 3.4.
- Direct database writes are outside DTO validation; the API rejects negative limits, but the current database schema has no nonnegative check constraint.
- `ExpenseService` can still auto-create an unknown category with a default limit when ingesting AI output. Category-name validation is addressed by a later AI safety fix.
- There are no frontend category or budget controls yet.
- A zero limit means alerts are disabled, not that every positive expense should trigger an alert.

### Interview review

Question: Why enforce case-insensitive uniqueness in both the service and database?

Answer: The service gives callers a clear domain error before attempting the write. The database index preserves the invariant during concurrent requests where both service checks could otherwise pass.

Question: Why block deletion when transactions reference a category?

Answer: Historical transactions need their category meaning. Returning HTTP 409 preserves ledger integrity and communicates that the valid request conflicts with current resource state.

Question: Why keep the existing budget-status endpoint in the same service?

Answer: CRUD manages the category and its cap, while budget status combines that cap with monthly ledger data. Keeping the verified query intact avoids breaking the notification consumer contract.

## Fix 3.3 Transaction ledger update and delete

Status: complete and verified on 2026-11-25.

Checklist item: Add transaction ledger update and delete operations that keep account balances and monthly budget totals consistent.

### Why this fix was needed

The ledger previously supported creation and listing only. A user could not correct the amount, account, category, merchant, or type of a mistaken transaction, and could not remove an accidental entry.

Changing or deleting only the transaction row would corrupt the account balance. For example, changing a stored expense of 100 to income of 50 requires undoing the original 100 deduction before adding the new 50 increase.

### What changed

The expense service now provides:

| Method and path | Behavior |
| --- | --- |
| `GET /api/v1/expenses/{transactionId}` | Return one ledger transaction or HTTP 404. |
| `PUT /api/v1/expenses/{transactionId}` | Replace editable transaction fields and correct affected balances. |
| `DELETE /api/v1/expenses/{transactionId}` | Reverse the stored balance effect and delete the row. |

`PUT` uses full replacement semantics for account, category, amount, type, merchant, and raw input. It preserves the original transaction ID and creation timestamp.

The shared `ExpenseRequestDTO` now also validates:

- a positive account ID;
- a category name no longer than 50 characters;
- a positive amount with at most 10 integer and 2 fractional digits;
- transaction type `EXPENSE` or `INCOME`, ignoring letter case;
- a merchant no longer than 100 characters.

The service normalizes transaction type to uppercase and trims category and merchant values. A missing type continues to default to `EXPENSE`.

### Balance correction algorithm

Update loads and validates the complete target state before changing balances. Inside one PostgreSQL transaction it then:

1. loads the existing ledger transaction;
2. resolves the target account and category;
3. validates and normalizes the target transaction type;
4. reverses the old transaction effect on the old account;
5. applies the new effect to the target account;
6. saves affected accounts and the updated transaction.

```text
Old EXPENSE 100 on account A
    |
    +---- reverse old effect: account A plus 100
    |
New INCOME 50 on account B
    |
    +---- apply new effect: account B plus 50
```

When the account does not change, the same managed account is corrected and saved once. When the account changes, both accounts are updated in the same database transaction.

Delete uses the stored transaction type and amount:

- deleting an expense adds its amount back;
- deleting income subtracts its amount;
- the ledger row is then removed in the same database transaction.

Any failure rolls back both balance and ledger changes together.

### Budget total consistency

Monthly category spending is calculated from current rows whose type is `EXPENSE`. No separate cached total is updated.

Therefore:

- changing an expense amount changes the sum;
- moving an expense to another category moves its contribution;
- changing an expense to income removes it from spending;
- deleting an expense removes it from spending.

The existing `throughTransactionId` budget-status query remains compatible because updates preserve the transaction ID.

### Resulting flow

```text
Validated PUT or DELETE request
    |
    v
Load existing transaction
    |
    v
Reverse old account balance effect
    |
    +---- PUT: apply replacement effect and save transaction
    |
    +---- DELETE: remove transaction
    |
    v
Commit account and ledger changes atomically
    |
    v
Dashboard and budget queries read corrected rows
```

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/controller/ExpenseController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/ExpenseRequestDTO.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/controller/ExpenseControllerTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceLedgerMutationTest.java`
- `docs/05_API_CONTRACT.md`

### Verification

```bash
cd backend/expense-service
mvn test
```

Thirty-seven expense-service tests passed with no failures or errors. Six ledger mutation service tests and three controller tests cover:

- correcting an expense amount on the same account;
- moving a transaction between accounts;
- changing an expense into income;
- rejecting an invalid transaction type before balance mutation;
- deleting an expense and restoring its deduction;
- deleting income and removing its increase;
- missing transaction responses;
- request validation and HTTP 204 deletion.

The existing outbox failure test intentionally logs a `broker unavailable` stack trace.

### Live PostgreSQL verification

The updated service ran on temporary port 18081 with a temporary account, category, and transaction:

1. The account was created with an opening balance of 1000.
2. Creating an expense of 100 changed the balance to 900 and category spending to 100.
3. Updating the same transaction to income of 50 changed the balance to 1050.
4. The budget-status query changed spending from 100 to 0 because income is excluded.
5. `GET /api/v1/expenses/{id}` returned the corrected income transaction while preserving its ID and creation time.
6. Deleting the income returned HTTP 204 and restored the opening balance to 1000.
7. The budget total remained 0 and reading the deleted transaction returned HTTP 404.
8. The temporary category, account, and published outbox row were deleted.
9. RabbitMQ reported zero ready and zero unacknowledged messages after the check.

### Known limitations

- Concurrent writes to one account can still overwrite balances. Pessimistic or optimistic locking is Fix 3.5.
- Ledger access is not scoped to the authenticated owner. Ownership is Fix 3.4.
- Update and delete do not publish dedicated domain events. The current event contract describes creation only, so budget alerts are not retracted or newly emitted after corrections.
- A category unknown to the service can still be auto-created with the existing default budget. AI category validation remains later work.
- The frontend has no ledger edit or delete controls.

### Interview review

Question: Why reverse the old effect instead of calculating the balance from scratch?

Answer: Reversal applies the exact delta needed inside the same transaction and avoids summing the entire ledger on every edit. Reconciliation queries can still independently verify balances later.

Question: Why validate the complete replacement before changing the balance?

Answer: It avoids partially mutating managed entities before discovering an invalid account, category, amount, or type. The database transaction is still the final atomicity boundary.

Question: How do budget totals remain correct without another update statement?

Answer: Budget totals are aggregate queries over current expense rows. Updating or deleting the source row automatically changes the next aggregate result.

## Fix 3.4 Financial data ownership

Status: complete and verified on 2026-11-25.

Checklist item: Define account and category ownership and scope all financial reads, writes, aggregates, and notification budget checks to the authenticated user.

### Why this fix was needed

Accounts already stored `user_id`, but the API trusted a caller-supplied owner during creation and used unscoped repository queries for later reads. Categories were global, and transaction and dashboard queries could return or aggregate data from every account.

That meant an ID was effectively sufficient to access another user's financial resource. It also meant dashboard totals and recent transactions could combine unrelated users. Authentication alone does not solve this: authorization must be applied to every database query.

### Ownership model selected

The API gateway is the trust boundary for Phase 1:

1. A normal API credential maps to `GATEWAY_API_USER_ID`.
2. The SMS webhook credential maps separately to `GATEWAY_WEBHOOK_USER_ID`.
3. The gateway removes any caller-supplied `X-Authenticated-User` value.
4. It injects the user ID belonging to the accepted credential.
5. The private expense service requires that header on financial endpoints.

When authentication is disabled for loopback development, the gateway still injects the configured development identity. This keeps local behavior and ownership behavior consistent.

The shared token is deliberately a Phase 1 mechanism. It maps a credential to one configured identity; it is not a complete account login system.

### What changed in the gateway

`GatewayAuthenticationFilter` now authenticates the appropriate API or webhook credential, strips credential and identity headers, and forwards a trusted identity. A client cannot choose its owner by sending `X-Authenticated-User`.

`GatewaySecurityProperties` adds separate API and webhook user IDs. Startup validation requires both IDs to contain between 1 and 50 characters. The rate limiter uses the authenticated user as its preferred key and falls back to direct remote IP when no identity exists.

New configuration:

- `GATEWAY_API_USER_ID`, defaulting to `usr_abhishek`;
- `GATEWAY_WEBHOOK_USER_ID`, defaulting to `usr_abhishek`.

The expense service must remain private. The trusted header proves identity only because untrusted callers cannot bypass the gateway and set it directly.

### What changed in PostgreSQL

Flyway migration `V6__financial_data_ownership.sql`:

- adds nonnull `categories.user_id`;
- assigns existing categories to the seeded Phase 1 owner `usr_abhishek`;
- removes global category-name uniqueness;
- creates case-insensitive uniqueness on `(user_id, LOWER(name))`;
- indexes account and category owner columns.

Two users may now both have a category named `Food & Dining`, while one user cannot create case variants of the same name.

Transactions derive ownership from their account. The application resolves the category using the same authenticated owner before a transaction is saved.

### What changed in the expense service

All public financial controllers require the trusted user header and pass it into their service or repository operations.

Owner-scoped behavior now includes:

- account creation, lists, lookup, update, and delete;
- category creation, lists, lookup, update, delete, and budget status;
- transaction creation, natural-language ingestion, SMS ingestion, lists, lookup, update, and delete;
- dashboard account balances, category budgets, monthly expense sums, and recent transactions.

Account creation no longer accepts `userId` from the JSON body. The owner is supplied only by authenticated request context.

Repository lookups combine resource ID and owner, for example `findByIdAndUserId` and `findByIdAndAccount_UserId`. When the resource exists for another user, the scoped query returns no row and the API responds with HTTP 404. This avoids both unauthorized access and resource-existence disclosure.

### Notification ownership propagation

`ExpenseCreatedEvent` already contains the account owner. `BudgetAlertListener` now passes that event user ID to the expense-service budget-status call through the trusted internal header. The monthly sum query verifies both category and account owner, so one user's event cannot include another user's spending.

### Resulting flow

```text
Client credential
    |
    v
API gateway validates credential
    |
    +---- remove caller identity and credential headers
    +---- inject trusted X-Authenticated-User
    |
    v
Expense controller requires identity
    |
    v
Service and repository query by resource plus owner
    |
    +---- matching owner: return or mutate resource
    |
    +---- different owner: HTTP 404
```

### Main implementation areas

- `backend/api-gateway/src/main/java/com/lifemate/gateway/GatewayAuthenticationFilter.java`
- `backend/api-gateway/src/main/java/com/lifemate/gateway/GatewaySecurityProperties.java`
- `backend/api-gateway/src/main/java/com/lifemate/gateway/GatewayHeaders.java`
- `backend/api-gateway/src/main/java/com/lifemate/gateway/ClientKeyResolverConfig.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/config/RequestHeaders.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/controller/AccountController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/controller/CategoryBudgetController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/controller/ExpenseController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/controller/DashboardController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/AccountService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/CategoryBudgetService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/main/resources/db/migration/V6__financial_data_ownership.sql`
- `backend/notification-service/src/main/java/com/lifemate/notification/client/ExpenseServiceClient.java`
- `backend/notification-service/src/main/java/com/lifemate/notification/listener/BudgetAlertListener.java`
- `docs/04_DATABASE.md`
- `docs/05_API_CONTRACT.md`

### Verification

Regression suites:

```bash
cd backend/api-gateway && mvn test
cd backend/expense-service && mvn test
cd backend/notification-service && mvn test
```

Results:

- 9 gateway tests passed with no failures or errors;
- 40 expense-service tests passed with no failures or errors;
- 4 notification-service tests passed with no failures or errors.

Focused tests cover spoofed-header replacement, identity injection with local authentication disabled, cross-user account and transaction lookup, per-user category names, owner-scoped repository calls, and propagation of the event user into notification budget lookup. The expense-service suite's `broker unavailable` stack trace is expected from the outbox retry test.

### Live two-user isolation check

The updated expense service ran on temporary port 18081 and Flyway successfully migrated the local schema from V5 to V6.

Temporary users A and B each created an account, the same category name, and a different expense:

- user A saw only its account with balance 900, its 100 expense, and dashboard monthly spending of 100;
- user B saw only its account with balance 1700, its 300 expense, and dashboard monthly spending of 300;
- both users successfully created the same category name under different category IDs;
- user B received HTTP 404 when requesting user A's account, category, or transaction IDs.

A temporary gateway on port 18080 was configured as user A. A request deliberately supplied a spoofed user B header, but the gateway replaced it and returned only user A's account.

The two transactions, categories, accounts, and exact outbox rows were deleted after the check. RabbitMQ reported zero ready and zero unacknowledged messages, and both temporary service processes were stopped.

### Known limitations

- The Phase 1 shared-token model represents one configured user per credential. Real multi-user authentication still requires verified JWT or session claims and account lifecycle management.
- The expense service trusts the internal header, so network isolation is mandatory. Exposing it directly would let a caller forge identity.
- Existing categories are assigned to `usr_abhishek` during migration because the old schema contained no ownership information from which to infer another owner.
- Natural-language and SMS ingestion still default to account ID 1 when no account is supplied. A newly added user should supply an owned account until account selection is redesigned.
- Concurrent updates to the same account can still overwrite balances. This is Fix 3.5.

### Interview review

Question: What is the difference between authentication and ownership authorization here?

Answer: Authentication establishes who the caller is at the gateway. Authorization applies that identity to every resource query, so knowing another resource ID is not enough to access it.

Question: Why does a cross-user lookup return 404 instead of 403?

Answer: The owner-scoped query behaves as if the resource does not exist for that caller. This prevents leaking which IDs belong to other users while still denying access.

Question: Why overwrite the identity header at the gateway?

Answer: Headers from an untrusted client are only claims. The gateway removes the claim and inserts identity derived from a credential it validated, creating a clear trust boundary.

Question: Why make category names unique per user rather than globally?

Answer: Categories are personal financial configuration. Different users should be able to use the same familiar names, while one user's category list still needs case-insensitive uniqueness.

## Fix 3.5 Concurrent balance protection

Status: complete and verified on 2026-11-25.

Checklist item: Prevent simultaneous ledger writes from overwriting account balances and prove the behavior with a real concurrency test.

### Why this fix was needed

The account balance is stored as a value and changed with a read-calculate-write sequence. A normal database transaction makes each request atomic, but it does not automatically serialize two transactions that read the same row.

Without locking, this interleaving was possible:

```text
Request A reads 1000        Request B reads 1000
Request A subtracts 100     Request B subtracts 200
Request A writes 900        Request B writes 800
```

The correct balance is 700, but the last write could leave 800 and lose request A's deduction. Both ledger rows could still exist, making the stored balance disagree with the ledger.

### Locking decision

The expense service now uses PostgreSQL pessimistic row locking for balance-changing operations. Spring Data's `PESSIMISTIC_WRITE` lock produces `SELECT ... FOR NO KEY UPDATE` on PostgreSQL.

The first writer locks the account row until its database transaction commits or rolls back. A second writer for the same account waits. After the lock is released, the second request reads the newly committed balance and applies its own effect.

Pessimistic locking was selected because balance conflicts are resolved before calculation. The caller does not need to retry an optimistic-lock failure, and the ledger and balance remain in one transaction.

### What changed

`AccountRepository` now has separate write-locking queries for:

- one account by ID and authenticated owner;
- multiple owned accounts ordered by account ID.

The normal nonlocking repository methods remain available for reads.

`TransactionRepository` adds an owner-scoped write-lock query for edits and deletes. This prevents two requests from replacing or deleting the same ledger row at the same time.

`ExpenseService` now locks:

- the target account before creating an expense or income transaction;
- the transaction and affected accounts before replacing a transaction;
- the transaction and its account before deleting it.

When a transaction moves between accounts, the service deduplicates and sorts the account IDs before requesting locks. Consistent ordering reduces the chance that two opposite account moves deadlock while each waits for the other's row.

`AccountService` also locks an account before update or delete. This prevents a currency change or deletion decision from racing a new ledger write.

No Flyway migration was needed because row locking changes transaction behavior, not table structure.

### Resulting flow

```text
Request A begins                         Request B begins
    |                                        |
    v                                        v
Lock account row                       Try to lock same row
    |                                        |
Read 1000 and write 900                      +---- waits
Insert ledger row                            |
Commit and release lock                      |
                                             v
                                      Lock becomes available
                                      Read committed 900
                                      Write 700
                                      Insert ledger row
                                      Commit
```

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/repository/AccountRepository.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/repository/TransactionRepository.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/AccountService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/AccountBalanceConcurrencyIntegrationTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/AccountServiceTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceEventTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceLedgerMutationTest.java`
- `docs/04_DATABASE.md`
- `docs/05_API_CONTRACT.md`

### Verification

The normal regression suite ran with:

```bash
cd backend/expense-service
mvn test
```

Forty tests passed with no failures or errors. The PostgreSQL-only concurrency test is skipped by default so ordinary unit tests do not require local infrastructure. The existing outbox failure test still emits its expected `broker unavailable` stack trace.

The real concurrency test ran separately against the local PostgreSQL 15 container:

```bash
RUN_POSTGRES_INTEGRATION_TESTS=true \
  mvn -Dtest=AccountBalanceConcurrencyIntegrationTest test
```

Observed behavior:

1. A temporary account started at 1000.
2. The first transaction created a 100 expense but deliberately stayed uncommitted while holding the account lock.
3. A simultaneous 200 expense did not finish during a 500 ms timeout because it was waiting for the same account row.
4. After the first transaction committed, the second continued.
5. PostgreSQL logged `FOR NO KEY UPDATE` for both account reads.
6. The final balance was 700 and both ledger rows existed.
7. The integration test passed with no failures or errors in 16.89 seconds.
8. The temporary transactions, category, and account were deleted by test cleanup.

### Known limitations

- Contending writes wait, so a very busy single account can have higher latency. Metrics and an explicit lock timeout may be useful before production scale.
- A process must keep database transactions short; slow network calls must not be added after acquiring the account lock. Current AI parsing occurs before `createExpense` acquires the lock.
- Lock ordering reduces deadlock risk for multi-account edits, but production code should still treat database deadlock exceptions as retryable failures.
- The concurrency integration test is opt-in because it requires a configured PostgreSQL instance.
- This fix protects stored account balances. It does not add concurrent safeguards to unrelated category-management operations.

### Interview review

Question: Why is `@Transactional` alone insufficient for this balance update?

Answer: A transaction makes one request atomic, but under the usual isolation level two requests can still read the same old value and overwrite one another. The row lock serializes the read-calculate-write sequence.

Question: Why use pessimistic instead of optimistic locking?

Answer: A financial balance conflict should be resolved before applying the calculation. Pessimistic locking makes the second writer wait and then calculate from the latest committed balance, rather than failing at commit and requiring an application retry.

Question: Why lock multiple accounts in ID order?

Answer: Consistent lock ordering prevents two account-transfer-style updates from each holding one row while waiting for the other in the opposite order, which is a common deadlock pattern.

Question: Does this block dashboard reads?

Answer: The application uses locking repository methods only for writes. PostgreSQL's normal MVCC behavior lets ordinary reads continue using the last committed version.

## Fix 4.1 Manual categorization fallback

Status: complete and verified on 2026-11-25.

Checklist item: Allow natural-language and SMS transaction entry to proceed with user-supplied amount, category, and merchant details when AI parsing is unavailable or unusable.

### Why this fix was needed

Both ingestion paths depended on the AI service to produce every value required to save a ledger transaction. An outage, timeout, empty response, or unusable amount/category failed the request with no recovery path. Automatically guessing financial values would risk recording an incorrect balance change, so the fallback needs explicit human-provided transaction details.

### Design decision

Both request DTOs accept an optional nested `manualFallback` object. When present, it is validated and AI is skipped. When it is absent, the service calls AI and checks the minimum persisted fields: amount must be positive with at most two decimal places, and category must be present and fit the category field limit. An exception or unusable response becomes HTTP 503 with `manualFallbackRequired: "true"` and an explicit statement that no transaction was saved.

The frontend presents amount, category, and merchant inputs after that response, then retries the same natural-language request with `manualFallback`. For automated SMS, the caller can resubmit with the same object after a manual review. This avoids silently choosing `Other` or repeating the AI call during fallback.

### What changed

- Added `ManualExpenseFallbackDTO` with amount, category, and merchant validation.
- Added the optional nested fallback to natural-language and SMS request DTOs.
- Updated `ExpenseService` to bypass AI for explicit fallback data and to reject incomplete AI results before account or ledger mutation.
- Added `AiParsingUnavailableException` handling that returns 503 and a machine-readable manual-fallback indicator.
- Added a browser fallback form that supports standard and currently listed custom categories.
- Documented the retry contract in the API contract.

### Resulting flow

```text
Natural-language or SMS request
    |
    +---- manualFallback supplied: validate -> save as EXPENSE -> update balance/outbox
    |
    +---- no manualFallback: call AI
              |
              +---- usable response: save transaction
              |
              +---- outage or unusable response: HTTP 503, no financial mutation
                         |
                         +---- caller supplies manualFallback and retries
```

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/dto/ManualExpenseFallbackDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/NaturalLanguageExpenseRequestDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/SmsWebhookRequestDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/exception/AiParsingUnavailableException.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/exception/GlobalExceptionHandler.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceManualFallbackTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/AccountBalanceConcurrencyIntegrationTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/controller/ExpenseControllerTest.java`
- `frontend/src/App.jsx`
- `docs/05_API_CONTRACT.md`

### Verification

- `mvn -Dtest=ExpenseServiceManualFallbackTest,ExpenseControllerTest test`: 8 tests passed, no failures or errors. Covered AI outage, invalid AI amount, no account mutation on failed parsing, validated manual save, nested request validation, and the 503 response contract.
- `mvn test`: 46 tests run, no failures or errors; one opt-in PostgreSQL test was skipped in the normal suite.
- The opt-in `AccountBalanceConcurrencyIntegrationTest` ran against local PostgreSQL 15: 2 tests passed. The manual fallback case persisted both natural-language and SMS requests, updated the account balance by the combined amount, and verified AI was not called. The existing concurrent-balance case also passed.
- `npm run build`: passed.
- `npm run lint`: not clean due to the existing `react-hooks/set-state-in-effect` issue in `App.jsx` (Fix 5.2) and an existing undefined `process` global in `vite.config.js`.

### Cleanup

The PostgreSQL integration test uses a UUID-scoped owner and its `@AfterEach` cleanup deleted both temporary transactions, the category, and the account. The outbox writer was mocked, so no RabbitMQ messages were created. No persistent process was started.

### Known limitations

- SMS automation cannot interactively resolve an AI failure. It receives the 503 response and must be reviewed and retried with `manualFallback` by its caller.
- The manual fallback is saved as `EXPENSE`; SMS debit/credit/nontransaction classification remains Fix 4.2.
- Supported category-name and currency/account-currency validation remain Fix 4.4. The fallback enforces required category presence and length, not an allowlist.
- The frontend continues to use its existing default account behavior.
- Frontend lint remains blocked by the separately tracked lint issues noted above.

### Interview review

Question: Why return 503 instead of creating an `Other` transaction when AI fails?

Answer: A guessed amount or category can change a financial balance incorrectly. The API makes the incomplete state explicit and saves nothing until a person supplies and validates the missing values.

Question: Why skip AI when `manualFallback` is present?

Answer: The caller has already supplied the reviewed values. Skipping another remote call keeps the fallback deterministic and avoids waiting through another AI timeout.

Question: How does the service prevent a malformed AI response from changing the balance?

Answer: It checks required persisted fields before calling the transaction save path. A rejected response becomes a 503, and tests assert that account lookup and mutation do not happen.

## Fix 4.2 SMS transaction classification

Status: complete and verified on 2026-11-25.

Checklist item: Distinguish debit, credit, and nontransaction SMS messages so account balances and the ledger reflect only completed transactions with the correct transaction type.

### Why this fix was needed

The SMS ingestion path previously treated every parsed message as an expense. A credit such as a salary deposit could therefore decrease the account balance, while an OTP, balance notice, or failed payment could be recorded as a financial transaction.

### Design decision

The AI response now carries a constrained `transaction_type`: `EXPENSE`, `INCOME`, or `NON_TRANSACTION`. The model is instructed to classify only completed debit/credit activity, and the response schema permits financial fields to be absent for nontransactions while requiring positive amount and category for completed transactions.

The expense service maps debits to `EXPENSE` and credits to `INCOME`, reusing the existing atomic balance and outbox path. A `NON_TRANSACTION` result returns HTTP 422 before account lookup or mutation. Older AI responses without a type and manual SMS retries without an explicit type default to `EXPENSE`; manual retries can specify `INCOME` for a credit.

### What changed

- Added the three-way transaction type to the AI response schema and Gemini classification instructions.
- Updated `AiAgentClient` and `ExpenseService` to accept detail-free nontransactions, validate completed-transaction fields, map credits to income, and reject nontransactions without saving.
- Added an HTTP 422 response for messages that do not represent a completed transaction.
- Added an optional `transactionType` to manual fallback data, restricted to `EXPENSE` or `INCOME`.
- Updated the AI and SMS API contract documentation.
- Added Java service/controller tests, Python schema tests, and an opt-in PostgreSQL integration case.

### Resulting flow

```text
SMS body -> AI classification
    |
    +---- EXPENSE: save ledger row and subtract amount from balance
    |
    +---- INCOME: save ledger row and add amount to balance
    |
    +---- NON_TRANSACTION: HTTP 422; no account or ledger mutation
```

### Main implementation areas

- `backend/ai-agent-service/app/models/expense_schema.py`
- `backend/ai-agent-service/app/services/gemini_parser.py`
- `backend/ai-agent-service/tests/test_expense_schema.py`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/AiParseResponseDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/ManualExpenseFallbackDTO.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/client/AiAgentClient.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/exception/NonTransactionMessageException.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/exception/GlobalExceptionHandler.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceManualFallbackTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/controller/ExpenseControllerTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/AccountBalanceConcurrencyIntegrationTest.java`
- `docs/05_API_CONTRACT.md`

### Verification

- `mvn -Dtest=ExpenseServiceManualFallbackTest,ExpenseControllerTest test`: 12 tests passed with no failures or errors. Covered debit expense, credit income, no-save nontransaction behavior, and HTTP 422.
- `mvn test`: 52 tests, no failures or errors; 3 opt-in PostgreSQL integration tests were skipped by default.
- `RUN_POSTGRES_INTEGRATION_TESTS=true DB_URL=... DB_USERNAME=... DB_PASSWORD=... mvn -Dtest=AccountBalanceConcurrencyIntegrationTest test`: 3 PostgreSQL integration tests passed. The test confirmed a credit changed a real balance from 1000 to 1050, an OTP did not change the balance or transaction count, and existing manual-fallback and concurrent-balance checks still passed.
- `.venv/bin/python -m unittest discover -s tests -v`: 3 AI schema tests passed for valid income, required transaction details, and detail-free nontransactions.
- `git diff --check`: passed.

The unit and integration tests used controlled AI responses. No live Gemini request was made.

### Cleanup

The opt-in database test used its existing UUID-scoped owner; `@AfterEach` removed its temporary transactions, category, and account. The outbox writer was mocked, so no RabbitMQ messages were created. No persistent process was started.

### Known limitations

- Classification quality depends on the configured Gemini model and has not been measured against a representative bank-SMS corpus or a live Gemini call.
- Manual fallback defaults to `EXPENSE` unless the caller explicitly supplies `transactionType: "INCOME"` for a credit.
- Sensitive transaction logging is addressed in Fix 4.5.
- Duplicate webhook submissions can still create duplicate transactions; idempotency remains Fix 4.3.

### Interview review

Question: Why represent `NON_TRANSACTION` separately instead of assigning the message to `Other`?

Answer: `Other` still creates a financial ledger row and changes a balance. An OTP or failed payment is not a transaction at all, so the API rejects it before any financial mutation.

Question: How does a credit affect the ledger safely?

Answer: It is persisted as `INCOME` and uses the existing locked, transactional balance path, which adds the amount and writes the transaction and outbox event atomically.

Question: What happens if an older AI response omits `transaction_type`?

Answer: It defaults to `EXPENSE` for compatibility with the prior debit-focused parser. The deployed AI service should be upgraded with the expense service to enable credit and nontransaction recognition.

## Fix 4.4 AI output and currency validation

Status: complete and verified on 2026-11-27.

Checklist item: Validate supported category names, positive amount, currency, and account currency before saving. Document INR-only handling because the ledger stores no transaction currency.

### Why this fix was needed

The expense service previously accepted AI category text and could auto-create an unknown category. It also accepted the AI response currency without checking it and could save a transaction into an account with a different or unsupported currency. Because the ledger stores amount but no transaction-currency column, accepting non-INR values would imply that balances and amounts share an undefined currency.

### What changed

- Defined the supported category allowlist in the expense service and stopped accepting unknown category names.
- Preserved a known supported category name as canonical spelling and created only supported categories for new owners.
- Added positive amount, two-decimal precision, and 10-integer-digit validation before account mutation for create and update operations.
- Made the ledger policy explicit: supported transaction and account currency is only `INR`.
- Normalized AI and requested currency to uppercase and rejected `USD` or other values before saving.
- Added Python Pydantic validation for unsupported AI categories and non-INR currencies.
- Updated the API contract to document the INR-only policy and category allowlist.

### Resulting flow

```text
AI or manual input
    |
    v
Validate ledger currency = INR
    |
    v
Validate positive amount and supported category
    |
    v
Lock target account
    |
    v
Reject account unless its currency is INR
    |
    v
Save ledger transaction and balance mutation
```

Invalid AI amount, category, or currency returns the existing AI 503 manual-fallback path. Unsupported request category/currency and non-INR account currency return HTTP 400-style domain errors before ledger mutation.

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/AiParseResponseDTO.java`
- `backend/ai-agent-service/app/models/expense_schema.py`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceManualFallbackTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceLedgerMutationTest.java`
- `backend/ai-agent-service/tests/test_expense_schema.py`
- `docs/05_API_CONTRACT.md`

### Verification

- Focused Maven tests: 23 tests passed, no failures or errors.
- `mvn test`: 56 tests passed, 3 PostgreSQL integration tests skipped by default, no failures or errors.
- AI schema unittest suite: 5 tests passed.
- `git diff --check`: passed.

No live PostgreSQL or Gemini request was run. No live infrastructure was started.

### Known limitations

- The ledger has no transaction-currency column and intentionally does not support conversion or mixed-currency balances.
- Account creation can still accept assigned ISO 4217 currencies; transaction entry rejects non-INR accounts explicitly.
- Category and currency validation are implemented in both producer and consumer layers, but no database check constraints were added.

### Interview review

Question: Why reject non-INR instead of storing the AI currency separately?

Answer: The ledger and account balance are numeric amounts with one account currency. Adding a transaction currency without adding account conversion and historical currency rules would create ambiguous balances. The demo uses an explicit INR-only policy.

Question: Why validate categories in Java and Pydantic?

Answer: Pydantic prevents unsupported model output at the AI service boundary; Java independently protects against malformed AI HTTP responses, manual fallback misuse, and direct ledger calls.

## Fix 4.5 Sensitive transaction logging

Status: complete and verified on 2026-11-27.

Checklist item: Avoid logging full bank SMS or personal transaction text at INFO level.

### Why this fix was needed

The expense service logged raw natural-language input, SMS sender, transaction amounts, and AI-parsed merchant/category details at INFO level. The AI service also emitted exception tracebacks on parse failures, which could include sensitive values in third-party exception messages.

### What changed

- Replaced expense-service messages containing amount, account ID, transaction ID, and SMS sender with generic operational events.
- Removed raw input and parsed amount/category/merchant values from `AiAgentClient` INFO logs.
- Kept the Gemini model name as operational metadata but ensured parser logs do not include request text.
- Changed AI route failure logging from `logger.exception` to an error containing only the exception class; the exception message and traceback are no longer logged.
- Added output-capture regression tests for SMS processing logs, AI client request/response logs, and Python AI route failure logs.

### Resulting flow

```text
Incoming text or SMS
    |
    +---- expense service: generic "received/creating/saved" events only
    |
    +---- AI client: generic parse-start/parse-complete events only
    |
    +---- Python parser: model name only; no prompt text
    |
    +---- AI failure: exception type only; no exception message or traceback
```

### Main implementation areas

- `backend/expense-service/src/main/java/com/lifemate/expense/client/AiAgentClient.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/client/AiAgentClientTest.java`
- `backend/expense-service/src/test/java/com/lifemate/expense/service/ExpenseServiceManualFallbackTest.java`
- `backend/ai-agent-service/app/services/gemini_parser.py`
- `backend/ai-agent-service/app/api/routes.py`
- `backend/ai-agent-service/tests/test_routes_logging.py`

### Verification

- `mvn -Dtest=AiAgentClientTest,ExpenseServiceManualFallbackTest test`: 12 tests passed; assertions confirmed raw SMS text, sender, merchant, and amount do not appear in captured Java logs.
- `mvn test`: 58 tests run; 55 passed and 3 opt-in PostgreSQL integration tests were skipped. No failures or errors.
- `.venv/bin/python -m unittest discover -s tests -v`: 6 tests passed, including a failure-path assertion that the exception message is omitted from logs.
- `git diff --check`: passed.

No live AI provider request or application infrastructure was used.

### Known limitations

- This change controls application logging paths. Logging performed internally by third-party HTTP/AI libraries is not configured or verified here.
- Raw text remains part of transaction `raw_input` storage and can be returned through existing APIs; this fix changes logging behavior only.

### Interview review

Question: How can operations still diagnose AI failures without exposing SMS contents?

Answer: The AI API logs the exception class but omits the exception message and traceback, while the Java services emit generic lifecycle events. This preserves basic failure visibility without placing message contents or parsed financial details into application logs.

## Fix 5.1 Dashboard UI states

Status: implemented and build/targeted-lint verified on 2026-11-26.

Checklist item: Implement explicit loading, error with retry, empty, and data states for the dashboard, budget gauges, and transaction list as required by `docs/06_UI_FLOW.md`.

### Why this fix was needed

The dashboard previously rendered no budget or transaction content when the returned arrays were empty. If the summary request failed, the user received a toast near the quick-entry form, while the dashboard itself had no clear recovery action. The full-screen loading view also hid the rest of the app while the summary request was pending.

### What changed

- Added dashboard skeleton placeholders while the initial summary request is pending, leaving the quick-entry area available.
- Added an inline error state and Retry action when the initial summary request fails.
- Kept previously loaded dashboard data visible if a later refresh fails, with an alert and retry action.
- Added explicit empty messages for category budgets and recent transactions.
- Updated the UI flow documentation to describe inline alerts and retained data after refresh errors.

### Resulting flow

```text
Dashboard summary request
    |
    +---- pending, no data: show dashboard skeletons
    |
    +---- failed, no data: show dashboard error and Retry
    |
    +---- succeeded: show metrics and data
              |
              +---- no budgets or transactions: show section-specific empty message
              |
              +---- refresh fails: retain data and show retryable alert
```

### Main implementation areas

- `frontend/src/App.jsx`
- `docs/06_UI_FLOW.md`

### Verification

- `npm --prefix frontend exec -- eslint frontend/src/App.jsx`: passed.
- `npm --prefix frontend run build`: passed; Vite transformed 2,085 modules and generated the production bundle.
- `npm --prefix frontend run lint`: the dashboard component error is resolved; lint still fails on the pre-existing undefined `process` global in `frontend/vite.config.js`.
- `git diff --check`: passed for tracked changes after implementation.

No browser-based interaction test or live API smoke test was run; the frontend has no configured test runner and no browser executable was available in the environment.

### Known limitations and next step

The visual states have compile and targeted-lint verification but have not been exercised against controlled API responses in a browser. Add focused frontend interaction coverage under Fix 7.4 when that deferred testing work is scheduled. Correct transaction type and amount presentation remains Fix 5.3.

### Interview review

Question: Why keep old dashboard data visible after a refresh fails?

Answer: A transient refresh failure should not erase valid data already on screen. The alert communicates that the data may be stale and offers a retry without turning a temporary network issue into an empty dashboard.

## Fix 5.2 React effect lint fix

Status: complete and targeted-lint verified on 2026-11-26.

Checklist item: Fix the `react-hooks/set-state-in-effect` failure in `frontend/src/App.jsx`.

### Why this fix was needed

The effect invoked a component-level function that performs state updates for the dashboard request. The React Hooks lint rule reported that effect call as a synchronous state update pattern.

### What changed

- Moved the initial request into the effect's asynchronous loading callback, with state updates occurring only after the request settles.
- Added an effect cleanup guard so a completed request does not update state after the component has unmounted.
- Kept user-triggered refreshes in an event handler and did not suppress or disable the lint rule.

### Resulting flow

```text
Component mounts -> effect starts async summary request
    -> response/error settles -> update dashboard state if still mounted
    -> component unmounts -> cleanup ignores any later response
```

### Main implementation areas

- `frontend/src/App.jsx`

### Verification

- `npm --prefix frontend exec -- eslint frontend/src/App.jsx`: passed, including the React Hooks rule.
- `npm --prefix frontend run build`: passed.
- `npm --prefix frontend run lint`: now reports only the unrelated existing `no-undef` error for `process` in `frontend/vite.config.js`.

### Known limitations and next step

Repository-wide lint is not fully clean until the Vite config's `process` global issue is addressed separately. That unrelated config change was not included in this fix. The next priority is the accurate ledger transaction details in Fix 5.3.

### Interview review

Question: How did you address the effect lint failure without suppressing it?

Answer: The effect now owns the initial asynchronous request and commits results after the request settles. Its cleanup guard ignores stale results after unmount, while manual refresh remains a user-event handler.

## Fix 5.3 Ledger transaction details

Status: complete and build/targeted-lint verified on 2026-11-27.

Checklist item: Add the planned columns or equivalent visible information for date, merchant, category, amount, and transaction type without presenting income as expense.

### Why this fix was needed

The dashboard received `transactionType` from the expense API, but the React ledger ignored it. Every row showed a rose negative amount with a downward icon, so income was presented as spending.

### What changed

- Added a visible `INCOME` or `EXPENSE` badge to each recent transaction.
- Used `transactionType` to select income or expense presentation.
- Rendered income with a green upward icon and `+` amount; expenses retain a rose downward icon and `-` amount.
- Added a year to the displayed date and fallbacks for missing merchant, category, or invalid date values.
- Updated the UI flow ledger description to explicitly require type-aware amounts.

### Resulting flow

```text
Dashboard API
    |
    v
recentTransactions[].transactionType
    |
    +---- INCOME -> green badge, upward icon, positive amount
    |
    +---- EXPENSE or missing type -> rose badge, downward icon, negative amount
```

### Main implementation areas

- `frontend/src/App.jsx`
- `docs/06_UI_FLOW.md`

### Verification

- `npm --prefix frontend run build`: passed.
- `npm --prefix frontend exec -- eslint frontend/src/App.jsx`: passed.
- `git diff --check`: passed.

No browser interaction test was available in the existing frontend setup.

### Known limitations

- Visual accuracy was compile and lint verified; it was not exercised in a browser against a live dashboard response.
- Fix 5.4 account/budget frontend controls remains optional unless the demo requires those management flows.

### Interview review

Question: What prevented income from being shown incorrectly?

Answer: The UI previously assumed every ledger row was an expense and ignored the API's `transactionType`. The fix makes the type control both presentation and amount sign.

## Fix 6.1 API path alignment

Status: complete and verified on 2026-11-27.

Checklist item: Align the documented API paths with the paths exposed by the application.

### Why this fix was needed

The API contract documented a parse-and-save route and an expenses-prefixed dashboard route that the backend does not expose. It also showed `rawInput` in the parse request example, while the endpoint accepts `rawText`. This could send API consumers to nonexistent routes or lead them to construct an invalid request.

### What changed

- Updated the parse endpoint in `docs/05_API_CONTRACT.md` to `POST /api/v1/expenses/parse`.
- Changed its request example field from `rawInput` to `rawText`.
- Updated the dashboard endpoint to `GET /api/v1/dashboard/summary`.
- Marked Fix 6.1 complete in `REMAINING_FIXES.md`.
- Left the backend routes, frontend calls, response examples, and event contract unchanged.

### Resulting flow

The documented parse and dashboard routes now match the Spring controller mappings and frontend requests. API clients can use the documented paths without relying on routes that are not implemented.

### Main implementation areas

- `docs/05_API_CONTRACT.md`
- `backend/expense-service/src/main/java/com/lifemate/expense/controller/ExpenseController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/controller/DashboardController.java`
- `backend/expense-service/src/main/java/com/lifemate/expense/dto/NaturalLanguageExpenseRequestDTO.java`
- `frontend/src/App.jsx`

### Verification

- Confirmed `ExpenseController` exposes `POST /api/v1/expenses/parse` and the request DTO contains `rawText`.
- Confirmed `DashboardController` exposes `GET /api/v1/dashboard/summary`.
- Confirmed the frontend calls both documented paths.
- `git diff --check`: passed.
- No application tests or live checks were needed for this documentation-only change.

### Known limitations

- This fix covers the route names and parse request-field example only. Response example accuracy and broader API/event contract maintenance remain separate work under Fix 6.4.

### Interview review

Question: Why did you update the API documentation instead of changing the routes?

Answer: The frontend and backend already used the same implemented routes. Updating the contract corrected the source of confusion without adding duplicate aliases or changing working application behavior.

## Fix 6.2 Category and seed alignment

Status: complete and verified on 2026-11-27.

Checklist item: Align documented category names and seed budgets with the AI schema and Flyway seed data.

### Why this fix was needed

The database documentation showed outdated category names and monthly budgets as part of the V1 schema migration. The actual category seed rows are in V2 and use a different set of names and budget values, matching the AI service's supported category names.

### What changed

- Removed the obsolete category and account seed statements from the V1 schema example.
- Added the actual V2 seeded category names and monthly budgets to `docs/04_DATABASE.md`.
- Documented that the seeded names match `CategoryType` in the AI schema.
- Marked Fix 6.2 complete in `REMAINING_FIXES.md`.
- Left application code and Flyway migrations unchanged.

### Resulting flow

The database documentation now separates the V1 schema from V2 category seed data. Readers see the same eight category names and monthly budget values that are seeded in a fresh database and accepted by the AI parser.

### Main implementation areas

- `docs/04_DATABASE.md`
- `backend/ai-agent-service/app/models/expense_schema.py`
- `backend/expense-service/src/main/resources/db/migration/V2__seed_default_data.sql`
- `backend/expense-service/src/main/java/com/lifemate/expense/service/ExpenseService.java`

### Verification

- Compared all eight documented category names with `CategoryType` and the expense service's supported-category set.
- Compared all eight monthly budget values with `V2__seed_default_data.sql`.
- `git diff --check`: passed.
- No application tests or live checks were needed for this documentation-only change.

### Known limitations

- Category names and budgets are documented as current seed defaults; they are not enforced as the only names or budgets users may create through the category management API.
- The V2 migration also seeds an account; this fix intentionally documents only the category values relevant to the task.

### Interview review

Question: What was wrong with the documented category seed data?

Answer: The documentation showed old category names and budgets under V1, while Flyway seeds categories in V2 using the names accepted by the AI schema. I updated the documentation to reflect the actual migration and preserved the code and migration history.

## Fix 8.2 Environment examples and startup configuration

Status: complete and verified on 2026-11-27.

Checklist item: Add `.env.example` files and startup instructions listing required database, RabbitMQ, AI, and service URL settings without committing credentials or provider secrets.

### Why this fix was needed

The startup guide referred to private `.env` files but did not provide safe templates. Docker Compose reads a repository-root `.env`, the Spring expense service already imports root and service-local env files, while the gateway and notification services did not load them. The AI service reads its own `.env` file.

### What changed

- Added a root `.env.example` containing local Compose, database, broker, service URL, gateway CORS/rate-limit, and retry defaults.
- Added `backend/ai-agent-service/.env.example` with a placeholder Gemini key and the configured model.
- Configured the API gateway and notification service to optionally import service-local and repository-root `.env` files, matching the expense service.
- Updated `run-command.md` with `cp -n` setup commands so existing private environment files are not overwritten.
- Documented that example database/broker values are local-only and that gateway authentication is disabled only for loopback development.
- Left all existing `.env` files untouched and did not add real credentials.

### Resulting configuration flow

```text
Copy root .env.example to root .env
    |
    +---- Docker Compose reads DB/container values
    |
    +---- Expense, gateway, and notification services import root .env
    |
    +---- AI service reads backend/ai-agent-service/.env
             (copy its example and add a private Gemini API key)
```

### Main implementation areas

- `.env.example`
- `backend/ai-agent-service/.env.example`
- `backend/api-gateway/src/main/resources/application.yml`
- `backend/notification-service/src/main/resources/application.yml`
- `run-command.md`

### Verification

- `docker compose --env-file .env.example -f docker/docker-compose.yml config --quiet`: passed.
- `cd backend/api-gateway && mvn test`: 9 tests passed.
- `cd backend/notification-service && mvn test`: 4 tests passed.
- `cd backend/expense-service && mvn test`: 58 tests run; no failures or errors; 3 opt-in PostgreSQL tests skipped.
- Verified both `.env.example` files are not ignored by Git.
- `git diff --check`: passed.

The services were not started for this task.

### Known limitations

- Example DB and RabbitMQ credentials are intentionally weak local-development defaults and must not be used for a public deployment.
- The Gemini example contains only a placeholder; users must provide their own secret locally.
- Environment-file import assumes commands run from each service directory as shown in `run-command.md`.

### Interview review

Question: How do developers configure the local stack without committing secrets?

Answer: The checked-in examples provide non-secret local defaults. Developers copy them with non-overwriting commands, put the provider key only in the ignored AI-service `.env`, and keep the gateway loopback-bound with authentication disabled only for local development.

## Next fix

The next release-priority task is Fix 6.3 Transaction entry latency target. Fix 6.4 API and event contract maintenance remains in Priority 3; Fix 8.4 Project README documentation is reserved for last as requested.

## Quick glossary

| Term | Meaning in this project |
| --- | --- |
| API gateway | The single client-facing service that routes and filters requests. |
| CORS | A browser-origin policy; it is not authentication. |
| Rate limiting | Restricting request frequency to reduce abuse or accidental overload. |
| Bearer token | A credential accepted based on possession. |
| Transactional outbox | A table that stores event intent atomically with business data for later publication. |
| At-least-once delivery | A message should eventually arrive but can arrive more than once. |
| Idempotency | Repeating an operation causes no additional effect after its first successful application. |
| Poison message | A permanently unprocessable message that repeatedly fails. |
| Fail closed | Refuse startup or access when security configuration is unsafe or incomplete. |
