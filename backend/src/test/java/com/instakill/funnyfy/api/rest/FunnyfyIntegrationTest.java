package com.instakill.funnyfy.api.rest;

import com.instakill.funnyfy.application.FunnyfyService;
import com.instakill.funnyfy.domain.TextRewriter;
import com.instakill.funnyfy.infrastructure.OpenAiChatTextRewriter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against the real wiring. The test profile points {@code instakill.llm.url} at a port nothing listens
 * on, so these tests cover the "LM Studio is not running" path end to end without needing a model.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FunnyfyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TextRewriter textRewriter;

    @Autowired
    private FunnyfyService funnyfyService;

    private Cookie register(String name) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @Test
    void springWiresTheOpenAiAdapterIntoTheService() {
        assertThat(textRewriter).isInstanceOf(OpenAiChatTextRewriter.class);
        assertThat(funnyfyService).isNotNull();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBlankText() throws Exception {
        Cookie token = register("funnyblank");
        mockMvc.perform(post("/api/funnyfy")
                        .cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.text").exists());
    }

    @Test
    void llmServerDownIsAGraceful503ProblemDetail() throws Exception {
        Cookie token = register("funnydown");
        mockMvc.perform(post("/api/funnyfy")
                        .cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Today I'm at my 7th Software Engineering lab.\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The local LLM is not reachable. Is the LM Studio server running?"));
    }
}
