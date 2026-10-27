package com.ielts.tracker.model;

public enum ReviewStatus {
    /** No review was asked for. */
    NONE,
    /** A review is suggested for the due date; waiting for the user to accept or ignore it. */
    SUGGESTED,
    ACCEPTED,
    IGNORED
}
