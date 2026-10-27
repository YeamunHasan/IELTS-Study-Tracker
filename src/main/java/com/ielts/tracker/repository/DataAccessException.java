package com.ielts.tracker.repository;

/** Unchecked wrapper so callers get one error type; the UI shows it in a dialog instead of failing silently. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
    public DataAccessException(String message) { super(message); }
}
