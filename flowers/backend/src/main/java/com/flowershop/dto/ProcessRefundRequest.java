package com.flowershop.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 执行退款请求DTO
 */
public class ProcessRefundRequest {

    @NotBlank(message = "微信支付退款单号不能为空")
    private String refundId;

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
    }
}
