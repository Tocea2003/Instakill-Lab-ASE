package com.instakill.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final Path basePath;

    public MediaWebConfig(@Value("${media.base-path:media}") String basePath) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Files.createDirectories(basePath);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Cannot create media directory " + basePath, e);
        }
        registry.addResourceHandler("/media/**").addResourceLocations(basePath.toUri().toString());
    }
}
