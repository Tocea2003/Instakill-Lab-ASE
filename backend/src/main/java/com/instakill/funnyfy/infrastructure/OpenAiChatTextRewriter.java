package com.instakill.funnyfy.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.instakill.common.error.BadGatewayException;
import com.instakill.common.error.UpstreamTimeoutException;
import com.instakill.common.error.UpstreamUnavailableException;
import com.instakill.funnyfy.domain.TextRewriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

/**
 * Adapter from {@link TextRewriter} to an OpenAI-compatible {@code POST /v1/chat/completions} endpoint,
 * such as the one LM Studio serves. See https://platform.openai.com/docs/api-reference/chat/create.
 *
 * <p>Every failure is translated into an {@code InstakillException} so the REST layer answers with a
 * ProblemDetail: connection refused -> 503, timeout -> 504, HTTP error or unusable body -> 502.
 */
@Component
public class OpenAiChatTextRewriter implements TextRewriter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatTextRewriter.class);

    private final RestClient client;
    private final LocalLlmProperties properties;

    @Autowired
    public OpenAiChatTextRewriter(LocalLlmProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory(properties)), properties);
    }

    /** For tests: the builder may be bound to a {@code MockRestServiceServer}. */
    public OpenAiChatTextRewriter(RestClient.Builder builder, LocalLlmProperties properties) {
        this.client = builder.build();
        this.properties = properties;
    }

    private static ClientHttpRequestFactory requestFactory(LocalLlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        // Buffer the body so it goes out with a Content-Length header instead of chunked encoding,
        // which not every OpenAI-compatible server accepts.
        return new BufferingClientHttpRequestFactory(factory);
    }

    @Override
    public String rewrite(String text) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(new Message("system", properties.systemPrompt()), new Message("user", text)),
                properties.temperature(),
                properties.maxTokens(),
                false);

        ChatCompletionResponse response;
        try {
            response = client.post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("Local LLM at {} answered HTTP {}: {}", properties.url(), e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            throw new BadGatewayException(
                    "The local LLM answered with HTTP " + e.getStatusCode().value()
                            + ". Is the model '" + properties.model() + "' loaded?", e);
        } catch (RestClientException e) {
            // A timeout can surface while connecting, while waiting for the status line or while reading
            // the body, and Spring wraps each case differently, so look at the cause chain.
            if (isTimeout(e)) {
                log.warn("Local LLM at {} timed out: {}", properties.url(), e.getMessage());
                throw new UpstreamTimeoutException(
                        "The local LLM took too long to answer. Try again or use a smaller model.", e);
            }
            if (e instanceof ResourceAccessException) {
                log.warn("Local LLM at {} is not reachable: {}", properties.url(), e.getMessage());
                throw new UpstreamUnavailableException(
                        "The local LLM is not reachable. Is the LM Studio server running?", e);
            }
            log.warn("Local LLM at {} sent an unreadable response: {}", properties.url(), e.getMessage());
            throw new BadGatewayException("The local LLM sent a response that could not be read", e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null) {
            throw new BadGatewayException("The local LLM response contained no message");
        }
        return response.choices().getFirst().message().content();
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    record Message(String role, String content) {
    }

    record ChatCompletionRequest(
            String model,
            List<Message> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }
}
