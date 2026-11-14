import logging
from fastapi import FastAPI
from app.api.routes import router as ai_router
from app.core.config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)

app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="Natural language and bank SMS expense parser powered by Gemini",
)

app.include_router(ai_router)


@app.get("/health", tags=["Health"])
def health_check() -> dict[str, str]:
    return {"status": "UP", "service": "ai-agent-service"}