package com.flowershop.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMvcConfigTest {

    @Test
    void resolvesMerchantDistFromActualMerchantFolder() throws Exception {
        WebMvcConfig config = new WebMvcConfig(null);
        Method resolveDistPath = WebMvcConfig.class.getDeclaredMethod("resolveDistPath", String.class, String.class);
        resolveDistPath.setAccessible(true);

        Path distPath = (Path) resolveDistPath.invoke(config, "../flower-merchant-web/", "flower-merchant-web");

        assertTrue(Files.exists(distPath.resolve("index.html")), "merchant dist index.html should exist");
        assertTrue(distPath.toString().contains("flower-merchant-web"), "merchant dist should resolve from flower-merchant-web");
    }
}