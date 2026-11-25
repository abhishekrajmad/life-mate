# Life Mate API Gateway

Spring Cloud Gateway is the local entry point for browser and API requests. It listens on `127.0.0.1:8080` by default and requires Redis for rate limiting.

## Run locally

Start Redis with `docker compose -f docker/docker-compose.yml up -d redis`, then run `mvn spring-boot:run` from this directory. Start the expense service on port 8081 and AI agent service on port 8000 for the default upstream URLs. The Vite development server proxies `/api` to this gateway.

## Routes

| Path | Upstream |
| --- | --- |
| `/api/v1/expenses` and `/api/v1/expenses/**` | Expense service |
| `/api/v1/accounts` and `/api/v1/accounts/**` | Expense service |
| `/api/v1/dashboard/**` | Expense service |
| `/api/v1/categories/**` | Expense service |
| `/api/v1/ai/**` | AI agent service |

The internal `/api/v1/budget-alerts` receipt endpoint is deliberately not routed through the gateway. The notification service calls the expense service directly.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Gateway port |
| `SERVER_ADDRESS` | `127.0.0.1` | Bind address |
| `EXPENSE_SERVICE_URL` | `http://localhost:8081` | Expense upstream |
| `AI_AGENT_URL` | `http://localhost:8000` | AI upstream |
| `REDIS_HOST`, `REDIS_PORT` | `localhost`, `6379` | Redis connection |
| `GATEWAY_ALLOWED_ORIGINS` | localhost Vite origins | Browser CORS allowlist |
| `GATEWAY_RATE_REPLENISH` | `10` | Tokens added per second |
| `GATEWAY_RATE_BURST` | `20` | Maximum tokens per client |
| `GATEWAY_AUTH_ENABLED` | `false` | Require credentials for API routes |
| `GATEWAY_API_TOKEN` | none | Bearer token for normal API requests; minimum 32 characters |
| `GATEWAY_WEBHOOK_TOKEN` | none | Dedicated `X-Webhook-Token` value for the SMS webhook; minimum 32 characters |
| `GATEWAY_API_USER_ID` | `usr_abhishek` | User identity mapped to the normal API credential |
| `GATEWAY_WEBHOOK_USER_ID` | `usr_abhishek` | User identity mapped to the webhook credential |

Rate limiting uses the authenticated user ID after the gateway establishes an identity. Requests that do not have an identity, such as preflight requests, fall back to the direct remote IP address. Requests exceeding the limit receive HTTP 429. Forwarded IP headers are ignored because callers could forge them without a trusted reverse proxy.

Authentication is optional only while the gateway uses a loopback bind address. The application refuses to start on a non-loopback address unless authentication is enabled and the API and webhook tokens are distinct values of at least 32 characters. Generate random values and provide them through the environment; do not commit them.

Normal API calls send `Authorization: Bearer <GATEWAY_API_TOKEN>`. Phone automation calls to `/api/v1/expenses/webhook/sms` instead send `X-Webhook-Token: <GATEWAY_WEBHOOK_TOKEN>`. The gateway removes both credential headers before forwarding a validated request. Keep the upstream expense and AI services on a private network so callers cannot bypass the gateway.

The gateway also removes any caller-supplied `X-Authenticated-User` header and injects the user ID configured for the accepted credential. The expense service uses this trusted header to scope financial data. In loopback development with authentication disabled, the default API and webhook identities are still injected.

For a separately hosted frontend, set `VITE_API_BASE_URL` to the gateway's public URL ending in `/api/v1` at frontend build time, arrange for the client to obtain the API credential, and include the frontend origin in `GATEWAY_ALLOWED_ORIGINS`. The shared Bearer token is the deliberately simple Phase 1 mechanism; replace it with per-user JWT identity before supporting untrusted multi-user accounts.

For a gateway running on a nondefault local port, set `API_GATEWAY_PROXY_TARGET` when starting Vite, for example `API_GATEWAY_PROXY_TARGET=http://localhost:18080 npm run dev`.
