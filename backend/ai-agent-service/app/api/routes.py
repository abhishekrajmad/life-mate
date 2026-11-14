import logging
from fastapi import APIRouter, HTTPException, status
from app.models.expense_schema import ExpenseParseRequest, ExpenseParseResponse
from app.services.gemini_parser import GeminiParserService

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/ai", tags=["AI Expense Parser"])

parser_service = GeminiParserService()


@router.post(
    "/parse-expense",
    response_model=ExpenseParseResponse,
    status_code=status.HTTP_200_OK,
)
def parse_expense(payload: ExpenseParseRequest) -> ExpenseParseResponse:
    try:
        return parser_service.parse_expense(payload)
    except Exception as exc:
        logger.exception("Failed to parse expense via Gemini API")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"AI parsing failed: {str(exc)}",
        ) from exc