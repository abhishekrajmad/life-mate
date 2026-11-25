from typing import Literal
from pydantic import BaseModel, Field, field_validator, model_validator

CategoryType = Literal[
    "Food & Dining",
    "Groceries",
    "Transport",
    "Utilities & Bills",
    "Shopping",
    "Entertainment",
    "Health & Fitness",
    "Other",
]
TransactionType = Literal["EXPENSE", "INCOME", "NON_TRANSACTION"]


class ExpenseParseRequest(BaseModel):
    raw_text: str = Field(
        ...,
        min_length=2,
        max_length=1000,
        description="Unstructured manual expense note or raw bank SMS body",
        examples=["Spent 450 rupees on lunch at Paradise Biryani"],
    )
    currency: str = Field(
        default="INR",
        max_length=10,
        description="Default currency code if not explicitly stated in text",
    )


class ExpenseParseResponse(BaseModel):
    transaction_type: TransactionType = Field(
        default="EXPENSE",
        description="Expense for a debit, income for a credit, or non-transaction",
    )
    amount: float | None = Field(default=None, description="Extracted numeric transaction amount")
    currency: str | None = Field(default=None, description="3-letter currency code such as INR or USD")
    category: CategoryType | None = Field(default=None, description="Mapped transaction category")
    merchant: str | None = Field(default=None, description="Merchant, vendor, or short description")
    confidence_score: float = Field(
        ..., description="Model confidence score between 0.0 and 1.0"
    )

    @model_validator(mode="after")
    def validate_transaction_details(self) -> "ExpenseParseResponse":
        if self.transaction_type != "NON_TRANSACTION":
            if self.amount is None or self.amount <= 0:
                raise ValueError("Amount must be greater than 0 for a transaction")
            if self.category is None:
                raise ValueError("Category is required for a transaction")
        return self

    @field_validator("confidence_score")
    @classmethod
    def validate_confidence_range(cls, v: float) -> float:
        if not 0.0 <= v <= 1.0:
            raise ValueError("Confidence score must be between 0.0 and 1.0")
        return v