package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String openid = body.get("openid");
        String name = body.getOrDefault("name", "微信用户");
        return ApiResponse.success(userService.loginOrRegister(openid, name));
    }

    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> getUser(@PathVariable Long userId) {
        return ApiResponse.success(userService.getUserById(userId));
    }

    @PutMapping("/{userId}")
    public ApiResponse<String> updateProfile(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        userService.updateProfile(userId, body.get("name"), body.get("phone"), body.get("preferenceTags"));
        return ApiResponse.success("更新成功", null);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll() {
        return ApiResponse.success(userService.listAllUsers());
    }
}
