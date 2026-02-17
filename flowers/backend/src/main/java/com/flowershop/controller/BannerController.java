package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.BannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banners")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listBanners() {
        return ApiResponse.success(bannerService.listEnabled());
    }
}
