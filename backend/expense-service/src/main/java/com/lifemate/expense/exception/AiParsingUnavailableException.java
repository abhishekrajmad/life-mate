package com.lifemate.expense.exception;

public class AiParsingUnavailableException extends RuntimeException {

    public AiParsingUnavailableException(Throwable cause) {
        super("AI parsing is unavailable or returned invalid data. Provide manualFallback with amount and categoryName; no transaction was saved.",
                cause);
    }
}