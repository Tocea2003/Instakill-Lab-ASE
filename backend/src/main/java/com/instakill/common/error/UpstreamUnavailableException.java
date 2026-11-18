package com.instakill.common.error;

/** An upstream service could not be reached at all, e.g. connection refused (HTTP 503). */
public class UpstreamUnavailableException extends InstakillException {
    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
