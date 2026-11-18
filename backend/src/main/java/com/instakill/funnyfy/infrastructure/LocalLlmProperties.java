package com.instakill.funnyfy.infrastructure;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Settings for the OpenAI-compatible chat completions endpoint (LM Studio by default).
 * The URL has no default in code: it comes from {@code instakill.llm.url} / {@code LOCAL_LLM_URL}.
 */
@Validated
@ConfigurationProperties(prefix = "instakill.llm")
public record LocalLlmProperties(
        @NotNull URI url,
        @NotNull String model,
        @NotNull String systemPrompt,
        @DefaultValue("0.7") double temperature,
        @DefaultValue("256") int maxTokens,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout
) {
}
