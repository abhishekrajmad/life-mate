import os
import unittest
from unittest.mock import patch

os.environ.setdefault("GEMINI_API_KEY", "unit-test-placeholder")

from fastapi import HTTPException

from app.api.routes import parse_expense, parser_service
from app.models.expense_schema import ExpenseParseRequest


class ExpenseRouteLoggingTest(unittest.TestCase):
    def test_parser_failure_log_omits_exception_message(self):
        private_message = "PRIVATE_SMS_BODY_938475"
        request = ExpenseParseRequest(raw_text=private_message)

        with patch.object(
            parser_service,
            "parse_expense",
            side_effect=RuntimeError(private_message),
        ):
            with self.assertLogs("app.api.routes", level="ERROR") as captured:
                with self.assertRaises(HTTPException):
                    parse_expense(request)

        log_output = "\n".join(captured.output)
        self.assertIn("RuntimeError", log_output)
        self.assertNotIn(private_message, log_output)
