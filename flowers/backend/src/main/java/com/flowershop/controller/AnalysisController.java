package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.RecommendationResponse;
import com.flowershop.dto.ReplenishmentSuggestionResponse;
import com.flowershop.service.AnalysisQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisQueryService analysisQueryService;

    public AnalysisController(AnalysisQueryService analysisQueryService) {
        this.analysisQueryService = analysisQueryService;
    }

    @GetMapping("/replenishment")
    public ApiResponse<List<ReplenishmentSuggestionResponse>> replenishmentSuggestions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(analysisQueryService.listReplenishmentSuggestions(date));
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<RecommendationResponse>> userRecommendations(
        @RequestParam Long userId,
        @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ApiResponse.success(analysisQueryService.listRecommendations(userId, limit));
    }
}
