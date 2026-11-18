package com.instakill.common.error;

/** An upstream service did not answer in time (HTTP 504). */
public class UpstreamTimeoutException extends InstakillException {
    public UpstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
