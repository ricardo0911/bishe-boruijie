package com.flowershop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.web-root:../flower-web/}")
    private String webRoot;

    @Value("${app.upload-dir:uploads/}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");

        Path webRootPath = Paths.get(webRoot).toAbsolutePath().normalize();
        String webRootUri = webRootPath.toUri().toString();

        registry.addResourceHandler("/merchant/**")
                .addResourceLocations(webRootUri + "merchant/");

        registry.addResourceHandler("/admin/**")
                .addResourceLocations(webRootUri + "admin/");

        registry.addResourceHandler("/assets/**")
                .addResourceLocations(webRootUri + "assets/");

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uploadUri = uploadPath.toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadUri);
    }
}
