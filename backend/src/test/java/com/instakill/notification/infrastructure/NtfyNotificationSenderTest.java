package com.instakill.notification.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class NtfyNotificationSenderTest {

    @Test
    void postsMessageToTopic() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ntfy.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ntfy.test/my-topic"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Title", "New post"))
                .andExpect(content().string("hello"))
                .andRespond(withStatus(HttpStatus.OK));

        new NtfyNotificationSender(builder, "my-topic").send("New post", "hello");

        server.verify();
    }

    @Test
    void swallowsServerErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ntfy.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ntfy.test/t")).andRespond(withServerError());

        assertThatCode(() -> new NtfyNotificationSender(builder, "t").send("a", "b")).doesNotThrowAnyException();
    }
}
