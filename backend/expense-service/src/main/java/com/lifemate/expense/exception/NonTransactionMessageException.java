package com.lifemate.expense.exception;

public class NonTransactionMessageException extends RuntimeException {

    public NonTransactionMessageException() {
        super("Message does not contain a completed transaction; no transaction was saved.");
    }
}