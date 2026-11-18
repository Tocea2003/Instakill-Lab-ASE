package com.instakill.funnyfy.domain;

/**
 * Port for anything that can rewrite a piece of text (here: an LLM).
 *
 * <p>Implementations throw {@link com.instakill.common.error.UpstreamUnavailableException},
 * {@link com.instakill.common.error.UpstreamTimeoutException} or
 * {@link com.instakill.common.error.BadGatewayException} when the backing service fails.
 */
public interface TextRewriter {

    String rewrite(String text);
}
