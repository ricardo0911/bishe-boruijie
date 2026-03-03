package com.flowershop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminTokenInterceptor adminTokenInterceptor;

    @Value("${app.admin-web-root:../flower-admin-web/}")
    private String adminWebRoot;

    @Value("${app.merchant-web-root:../flower-web/}")
    private String merchantWebRoot;

    @Value("${app.upload-dir:uploads/}")
    private String uploadDir;

    public WebMvcConfig(AdminTokenInterceptor adminTokenInterceptor) {
        this.adminTokenInterceptor = adminTokenInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");

        String adminDistUri = toDistUri(adminWebRoot, "flower-admin-web");
        addSpaResourceHandler(registry, "/admin", adminDistUri);

        String merchantDistUri = toDistUri(merchantWebRoot, "flower-web");
        addSpaResourceHandler(registry, "/merchant", merchantDistUri);
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(merchantDistUri + "assets/");

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uploadUri = uploadPath.toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadUri);
    }

    private void addSpaResourceHandler(ResourceHandlerRegistry registry, String urlPrefix, String distUri) {
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(distUri)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        String normalizedPath = resourcePath == null ? "" : resourcePath;
                        if (normalizedPath.isBlank()) {
                            normalizedPath = "index.html";
                        }

                        Resource requestedResource = location.createRelative(normalizedPath);
                        if (requestedResource.exists() && requestedResource.isReadable() && checkResource(requestedResource, location)) {
                            return requestedResource;
                        }

                        if (normalizedPath.contains(".")) {
                            return null;
                        }

                        Resource indexResource = location.createRelative("index.html");
                        if (indexResource.exists() && indexResource.isReadable() && checkResource(indexResource, location)) {
                            return indexResource;
                        }
                        return null;
                    }
                });
    }

    private String toDistUri(String webRoot, String moduleDirName) {
        Path distPath = resolveDistPath(webRoot, moduleDirName);
        String distUri = distPath.toUri().toString();
        return distUri.endsWith("/") ? distUri : distUri + "/";
    }

    private Path resolveDistPath(String webRoot, String moduleDirName) {
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
            userDir.resolve(webRoot),
            userDir.resolve("backend").resolve(webRoot),
            userDir.resolve("flowers").resolve("backend").resolve(webRoot),
            userDir.resolve(moduleDirName),
            userDir.resolve("flowers").resolve(moduleDirName)
        );

        for (Path candidateRoot : candidates) {
            Path dist = candidateRoot.toAbsolutePath().normalize().resolve("dist");
            if (Files.exists(dist.resolve("index.html"))) {
                return dist;
            }
        }

        return userDir.resolve(webRoot).toAbsolutePath().normalize().resolve("dist");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/api/v1/**")
                // 排除不需要鉴权的路径
                .excludePathPatterns(
                        // 公开访问接口
                        "/api/v1/products",
                        "/api/v1/products/**",
                        "/api/v1/categories",
                        "/api/v1/categories/**",
                        "/api/v1/banners",
                        "/api/v1/banners/**",
                        "/api/v1/reviews",
                        "/api/v1/reviews/**",
                        // 用户登录注册相关
                        "/api/v1/users/login",
                        "/api/v1/users/register",
                        "/api/v1/users/*",
                        "/api/v1/users/*/addresses",
                        "/api/v1/users/*/addresses/**",
                        "/api/v1/users/*/favorites",
                        "/api/v1/users/*/favorites/**",
                        "/api/v1/users/*/coupons",
                        "/api/v1/users/*/coupons/**",
                        // 购物车（用户端需要token但不需要admin token）
                        "/api/v1/cart",
                        "/api/v1/cart/**",
                        // 订单（用户端）
                        "/api/v1/orders",
                        "/api/v1/orders/*",
                        "/api/v1/orders/user/**",
                        "/api/v1/orders/*/pay",
                        "/api/v1/orders/*/confirm",
                        "/api/v1/orders/*/cancel",
                        "/api/v1/payment/callback",
                        "/api/v1/payment/status/**",
                        // 上传接口（有单独的签名验证）
                        "/api/v1/upload",
                        "/api/v1/upload/**",
                        // 商家公开接口
                        "/api/v1/merchants/public",
                        "/api/v1/merchants/public/**"
                );
    }
}
