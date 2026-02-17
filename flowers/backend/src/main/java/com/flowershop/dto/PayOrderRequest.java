package com.flowershop.dto;

import jakarta.validation.constraints.NotBlank;

public class PayOrderRequest {

    @NotBlank(message = "paymentChannel不能为空")
    private String paymentChannel;

    private String paymentNo;

    public String getPaymentChannel() {
        return paymentChannel;
    }

    public void setPaymentChannel(String paymentChannel) {
        this.paymentChannel = paymentChannel;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }
}
