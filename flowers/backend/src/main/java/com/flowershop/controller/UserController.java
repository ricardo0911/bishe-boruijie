package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.exception.BusinessException;
import com.flowershop.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return ApiResponse.success(userService.login(body.get("account"), body.get("password")));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return ApiResponse.success(userService.register(
            body.get("account"),
            body.get("password"),
            body.get("name"),
            body.get("phone")
        ));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        userService.changePassword(parseUserId(body.get("userId")), body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.success("密码修改成功", null);
    }

    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> getUser(@PathVariable Long userId) {
        return ApiResponse.success(userService.getUserById(userId));
    }

    @PutMapping("/{userId}")
    public ApiResponse<String> updateProfile(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        userService.updateProfile(userId, body.get("name"), body.get("phone"));
        return ApiResponse.success("更新成功", null);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll() {
        return ApiResponse.success(userService.listAllUsers());
    }

    private Long parseUserId(String raw) {
        try {
            Long userId = Long.valueOf(String.valueOf(raw).trim());
            if (userId <= 0) {
                throw new NumberFormatException("userId must be positive");
            }
            return userId;
        } catch (Exception ex) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }
    }
}
