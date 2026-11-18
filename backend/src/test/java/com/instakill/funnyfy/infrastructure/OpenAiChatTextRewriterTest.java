package com.instakill.funnyfy.infrastructure;

import com.instakill.common.error.BadGatewayException;
import com.instakill.common.error.UpstreamTimeoutException;
import com.instakill.common.error.UpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiChatTextRewriterTest {

    private static final String URL = "http://llm.test/v1/chat/completions";

    private static LocalLlmProperties props(String url, Duration readTimeout) {
        return new LocalLlmProperties(URI.create(url), "google/gemma-3-270m", "Be funny.", 0.7, 256,
                Duration.ofSeconds(2), readTimeout);
    }

    @Test
    void sendsAnOpenAiChatCompletionRequestAndReturnsTheFirstChoice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("google/gemma-3-270m"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Be funny."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Today I'm at my 7th lab."))
                .andExpect(jsonPath("$.temperature").value(0.7))
                .andExpect(jsonPath("$.max_tokens").value(256))
                .andExpect(jsonPath("$.stream").value(false))
                .andRespond(withSuccess("""
                        {"id":"chatcmpl-1","object":"chat.completion","model":"google/gemma-3-270m",
                         "choices":[{"index":0,"finish_reason":"stop",
                                     "message":{"role":"assistant","content":"Lab 7: the sequel nobody asked for."}}],
                         "usage":{"prompt_tokens":20,"completion_tokens":9,"total_tokens":29}}
                        """, MediaType.APPLICATION_JSON));

        String result = new OpenAiChatTextRewriter(builder, props(URL, Duration.ofSeconds(5)))
                .rewrite("Today I'm at my 7th lab.");

        assertThat(result).isEqualTo("Lab 7: the sequel nobody asked for.");
        server.verify();
    }

    @Test
    void httpErrorFromTheModelServerIsABadGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"model not loaded\"}"));

        OpenAiChatTextRewriter rewriter = new OpenAiChatTextRewriter(builder, props(URL, Duration.ofSeconds(5)));

        assertThatThrownBy(() -> rewriter.rewrite("hi"))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("404");
    }

    @Test
    void responseWithoutChoicesIsABadGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        OpenAiChatTextRewriter rewriter = new OpenAiChatTextRewriter(builder, props(URL, Duration.ofSeconds(5)));

        assertThatThrownBy(() -> rewriter.rewrite("hi")).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void unreadableBodyIsABadGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        OpenAiChatTextRewriter rewriter = new OpenAiChatTextRewriter(builder, props(URL, Duration.ofSeconds(5)));

        assertThatThrownBy(() -> rewriter.rewrite("hi")).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void talksToARealServerWithAContentLengthHeader() throws Exception {
        // Some OpenAI-compatible servers reject chunked request bodies, so the adapter must send Content-Length.
        AtomicReference<String> contentLength = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ha ha\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiChatTextRewriter rewriter = new OpenAiChatTextRewriter(props(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions", Duration.ofSeconds(5)));

            assertThat(rewriter.rewrite("hi")).isEqualTo("ha ha");
            assertThat(contentLength.get()).isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void serverNotRunningIsServiceUnavailable() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort(); // free port, closed again: nothing listens there
        }
        OpenAiChatTextRewriter rewriter = new OpenAiChatTextRewriter(
                props("http://127.0.0.1:" + port + "/v1/chat/completions", Duration.ofSeconds(5)));

        assertThatThrownBy(() -> rewriter.rewrite("hi")).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void serverThatNeverAnswersIsAGatewayTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) { // accepts the connection, never replies
            OpenAiChatTextRewriter rewriter = new OpenAiChatTextRewriter(
                    props("http://127.0.0.1:" + silent.getLocalPort() + "/v1/chat/completions", Duration.ofMillis(300)));

            assertThatThrownBy(() -> rewriter.rewrite("hi")).isInstanceOf(UpstreamTimeoutException.class);
        }
    }
}
