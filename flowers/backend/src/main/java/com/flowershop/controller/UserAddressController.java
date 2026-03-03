package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.UserAddressService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/{userId}/addresses")
public class UserAddressController {

    private final UserAddressService userAddressService;

    public UserAddressController(UserAddressService userAddressService) {
        this.userAddressService = userAddressService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAddresses(@PathVariable Long userId) {
        return ApiResponse.success(userAddressService.listAddresses(userId));
    }

    @GetMapping("/default")
    public ApiResponse<Map<String, Object>> getDefaultAddress(@PathVariable Long userId) {
        return ApiResponse.success(userAddressService.getDefaultAddress(userId));
    }

    @GetMapping("/{addressId}")
    public ApiResponse<Map<String, Object>> getAddress(
        @PathVariable Long userId,
        @PathVariable Long addressId
    ) {
        return ApiResponse.success(userAddressService.getAddress(userId, addressId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createAddress(
        @PathVariable Long userId,
        @RequestBody Map<String, Object> body
    ) {
        return ApiResponse.success("Address created", userAddressService.createAddress(userId, body));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<Map<String, Object>> updateAddress(
        @PathVariable Long userId,
        @PathVariable Long addressId,
        @RequestBody Map<String, Object> body
    ) {
        return ApiResponse.success("Address updated", userAddressService.updateAddress(userId, addressId, body));
    }

    @PatchMapping("/{addressId}/default")
    public ApiResponse<Void> setDefaultAddress(
        @PathVariable Long userId,
        @PathVariable Long addressId
    ) {
        userAddressService.setDefaultAddress(userId, addressId);
        return ApiResponse.success("Default address updated", null);
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> deleteAddress(
        @PathVariable Long userId,
        @PathVariable Long addressId
    ) {
        userAddressService.deleteAddress(userId, addressId);
        return ApiResponse.success("Address deleted", null);
    }
}
