package com.flowershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 退款审核请求DTO
 */
public class AuditRefundRequest {

    @Size(max = 500, message = "拒绝原因不能超过500字")
    private String rejectReason;

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }
}
