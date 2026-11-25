import unittest

from pydantic import ValidationError

from app.models.expense_schema import ExpenseParseResponse


class ExpenseParseResponseTest(unittest.TestCase):
    def test_nontransaction_does_not_require_financial_details(self):
        response = ExpenseParseResponse(
            transaction_type="NON_TRANSACTION",
            confidence_score=0.99,
        )

        self.assertIsNone(response.amount)
        self.assertIsNone(response.category)

    def test_completed_transaction_requires_amount_and_category(self):
        for transaction_type in ("EXPENSE", "INCOME"):
            with self.subTest(transaction_type=transaction_type):
                with self.assertRaises(ValidationError):
                    ExpenseParseResponse(
                        transaction_type=transaction_type,
                        confidence_score=0.9,
                    )

    def test_completed_transaction_accepts_valid_details(self):
        response = ExpenseParseResponse(
            transaction_type="INCOME",
            amount=500,
            currency="INR",
            category="Other",
            merchant="Salary",
            confidence_score=0.95,
        )

        self.assertEqual("INCOME", response.transaction_type)
        self.assertEqual(500, response.amount)