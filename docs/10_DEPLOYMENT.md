# 10. DEPLOYMENT & CI/CD PIPELINE

## 1. Docker Multi-Container Configuration
All microservices feature dedicated multi-stage `Dockerfile` configurations and run locally via Docker Compose.

## 2. GitHub Actions CI/CD Pipeline (`.github/workflows/deploy.yml`)

```yaml
name: SLA CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build Expense Service
        run: |
          cd backend/expense-service
          mvn clean test package

      - name: Set up Python 3.11
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'

      - name: Test AI Agent Service
        run: |
          cd backend/ai-agent-service
          pip install -r requirements.txt
          pytest

  docker-build:
    needs: build-and-test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Docker Images
        run: |
          docker-compose -f docker/docker-compose.yml build
```

## 3. Production Cloud Hosting Matrix (Free Tier)
* **Frontend:** Vercel (Static React SPA hosting).
* **Spring Boot Services & FastAPI:** Render / Koyeb (Docker Web Services).
* **PostgreSQL Database:** Neon.tech (Serverless PostgreSQL).
* **Message Broker:** CloudAMQP (Free Lemur plan for RabbitMQ).
* **Redis Cache:** Upstash Redis (Serverless Redis).