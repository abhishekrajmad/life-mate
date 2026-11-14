import logging
from google import genai
from google.genai import types
from app.core.config import settings
from app.models.expense_schema import ExpenseParseRequest, ExpenseParseResponse

logger = logging.getLogger(__name__)

SYSTEM_INSTRUCTION = """
You are a financial transaction extraction engine for the Life Mate personal finance app.
Extract structured expense details from either:
1. Casual user notes (e.g., "Spent 450 on lunch at Paradise Biryani", "uber to office 220")
2. Automated bank/UPI debit SMS messages (e.g., "Rs. 450.00 debited from A/c XX1234 on 15-Nov-26 at Swiggy.")

Rules:
- Extract the exact numeric amount.
- Use the provided default currency unless another currency symbol/code is explicitly mentioned.
- Classify into the single best matching category from the schema enum.
- Extract a clean, concise merchant or vendor name (e.g., "Swiggy", "Paradise Biryani", "Uber"). If no specific merchant is named, provide a short 1-3 word summary (e.g., "Office Lunch", "Fuel").
- Assign a confidence_score between 0.0 and 1.0 based on clarity of amount and merchant.
"""


class GeminiParserService:
    def __init__(self) -> None:
        self.client = genai.Client(
            api_key=settings.gemini_api_key,
            http_options=types.HttpOptions(timeout=30000),  # 30-second timeout
        )

    def parse_expense(self, request: ExpenseParseRequest) -> ExpenseParseResponse:
        prompt = (
            f"Default Currency: {request.currency}\n"
            f"Transaction Text: {request.raw_text}"
        )

        logger.info("Sending transaction text to Gemini model: %s", settings.gemini_model)

        response = self.client.models.generate_content(
            model=settings.gemini_model,
            contents=prompt,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_INSTRUCTION,
                response_mime_type="application/json",
                response_schema=ExpenseParseResponse,
                temperature=0.1,
                automatic_function_calling=types.AutomaticFunctionCallingConfig(
                    disable=True
                ),
            ),
        )

        if response.parsed is not None:
            return response.parsed

        return ExpenseParseResponse.model_validate_json(response.text)