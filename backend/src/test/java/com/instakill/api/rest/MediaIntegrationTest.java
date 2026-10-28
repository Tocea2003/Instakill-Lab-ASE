package com.instakill.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie register(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("ACCESS_TOKEN");
    }

    @Test
    void uploadedImageIsResizedAndServedBack() throws Exception {
        Cookie token = register("mediauser");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB), "png", png);
        byte[] bytes = png.toByteArray();

        MvcResult upload = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "pic.png", "image/png", bytes))
                        .cookie(token))
                .andExpect(status().isCreated())
                .andReturn();

        String url = objectMapper.readTree(upload.getResponse().getContentAsString()).get("mediaUrl").asText();
        assertThat(url).startsWith("/media/").endsWith(".png");

        MvcResult fetched = mockMvc.perform(get(url).cookie(token)).andExpect(status().isOk()).andReturn();
        BufferedImage served = ImageIO.read(new ByteArrayInputStream(fetched.getResponse().getContentAsByteArray()));
        assertThat(served.getWidth()).isEqualTo(300);
        assertThat(served.getHeight()).isEqualTo(200);
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/media").file(new MockMultipartFile("file", "pic.png", "image/png", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDisallowedExtension() throws Exception {
        Cookie token = register("mediauser2");
        mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1}))
                        .cookie(token))
                .andExpect(status().isBadRequest());
    }
}
