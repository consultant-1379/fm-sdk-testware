package com.ericsson.oss.mediation.sdk.sdktestware;

public class OperatorException extends Exception {
    public OperatorException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public OperatorException(final String message) {
        super(message);
    }

    public OperatorException(final Throwable cause) {
        super(cause);
    }
}
