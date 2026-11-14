from typing import Literal
from pydantic import BaseModel, Field, field_validator

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
    amount: float = Field(..., description="Extracted numeric transaction amount")
    currency: str = Field(..., description="3-letter currency code such as INR or USD")
    category: CategoryType = Field(..., description="Mapped expense category")
    merchant: str = Field(..., description="Merchant, vendor, or short description")
    confidence_score: float = Field(
        ..., description="Model confidence score between 0.0 and 1.0"
    )

    @field_validator("amount")
    @classmethod
    def validate_positive_amount(cls, v: float) -> float:
        if v <= 0:
            raise ValueError("Amount must be greater than 0")
        return v

    @field_validator("confidence_score")
    @classmethod
    def validate_confidence_range(cls, v: float) -> float:
        if not 0.0 <= v <= 1.0:
            raise ValueError("Confidence score must be between 0.0 and 1.0")
        return v