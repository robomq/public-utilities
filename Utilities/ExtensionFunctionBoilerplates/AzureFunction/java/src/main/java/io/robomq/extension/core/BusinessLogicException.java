package io.robomq.extension.core;

/** Thrown by business logic to report a failure to H2R with its own error code. */
public class BusinessLogicException extends Exception {
    private final String errorCode;

    public BusinessLogicException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
