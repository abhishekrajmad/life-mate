# Life Mate API Gateway

Spring Cloud Gateway is the local entry point for browser and API requests. It listens on `127.0.0.1:8080` by default and requires Redis for rate limiting.

## Run locally

Start Redis with `docker compose -f docker/docker-compose.yml up -d redis`, then run `mvn spring-boot:run` from this directory. Start the expense service on port 8081 and AI agent service on port 8000 for the default upstream URLs. The Vite development server proxies `/api` to this gateway.

## Routes

| Path | Upstream |
| --- | --- |
| `/api/v1/expenses` and `/api/v1/expenses/**` | Expense service |
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

Rate limiting uses the direct remote IP address as its key. Requests exceeding the limit receive HTTP 429. Forwarded IP headers are ignored because callers could forge them without a trusted reverse proxy.

The current gateway has no user authentication. Keep the default loopback bind address for local development. Add authenticated user identity and a trusted proxy configuration before setting `SERVER_ADDRESS=0.0.0.0` or publishing the gateway to the internet. For a separately hosted frontend, set `VITE_API_BASE_URL` to the gateway's public URL ending in `/api/v1` at frontend build time and include the frontend origin in `GATEWAY_ALLOWED_ORIGINS`.

For a gateway running on a nondefault local port, set `API_GATEWAY_PROXY_TARGET` when starting Vite, for example `API_GATEWAY_PROXY_TARGET=http://localhost:18080 npm run dev`.
