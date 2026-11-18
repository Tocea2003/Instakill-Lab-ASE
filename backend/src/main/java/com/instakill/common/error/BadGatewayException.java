package com.instakill.common.error;

/** An upstream service answered, but with an error or an answer we cannot use (HTTP 502). */
public class BadGatewayException extends InstakillException {
    public BadGatewayException(String message) {
        super(message);
    }

    public BadGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
