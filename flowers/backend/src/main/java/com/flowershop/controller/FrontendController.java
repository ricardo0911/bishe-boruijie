package com.flowershop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class FrontendController {

    @GetMapping({"/admin", "/admin/", "/admin/{path:[^\\.]*}", "/admin/**/{path:[^\\.]*}"})
    public String forwardAdminRoutes() {
        return "forward:/admin/index.html";
    }

    @GetMapping({"/merchant", "/merchant/", "/merchant/{path:[^\\.]*}", "/merchant/**/{path:[^\\.]*}"})
    public String forwardMerchantRoutes() {
        return "forward:/merchant/index.html";
    }

    @GetMapping({"/merchat", "/merchat/"})
    public String redirectMerchantTypoRoot() {
        return "redirect:/merchant";
    }

    @GetMapping("/merchat/**")
    public String redirectMerchantTypoWithPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String suffix = requestUri.substring("/merchat".length());
        return "redirect:/merchant" + suffix;
    }
}
