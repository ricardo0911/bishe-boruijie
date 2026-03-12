package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request.loginType(), request.account(), request.password()));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(
                request.loginType(),
                request.account(),
                request.password(),
                request.displayName()
        ));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.loginType(), request.account(), request.oldPassword(), request.newPassword());
        return ApiResponse.success("Password updated, please sign in again", null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("X-Admin-Token") String token) {
        authService.logout(token);
        return ApiResponse.success("Signed out", null);
    }

    public record LoginRequest(
            @NotBlank(message = "loginType is required") String loginType,
            @NotBlank(message = "account is required") String account,
            @NotBlank(message = "password is required") String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank(message = "loginType is required") String loginType,
            @NotBlank(message = "account is required") String account,
            @NotBlank(message = "password is required") String password,
            String displayName
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "loginType is required") String loginType,
            @NotBlank(message = "account is required") String account,
            @NotBlank(message = "oldPassword is required") String oldPassword,
            @NotBlank(message = "newPassword is required") String newPassword
    ) {
    }
}
