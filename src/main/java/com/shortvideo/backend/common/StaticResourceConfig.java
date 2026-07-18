package com.shortvideo.backend.common;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final String avatarUploadDir;

    public StaticResourceConfig(@Value("${app.storage.avatar-upload-dir}") String avatarUploadDir) {
        this.avatarUploadDir = avatarUploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path avatarDir = Paths.get(avatarUploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/static/avatars/**")
                .addResourceLocations(avatarDir.toUri().toString() + "/");
    }
}
