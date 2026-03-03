package com.flowershop.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信支付回调请求DTO
 * 参考微信官方支付回调通知参数
 */
public class PaymentCallbackRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "微信支付订单号不能为空")
    private String transactionId;

    @NotBlank(message = "支付金额不能为空")
    private String totalFee;

    @NotBlank(message = "支付结果不能为空")
    private String resultCode;

    @NotBlank(message = "支付时间不能为空")
    private String timeEnd;

    private String sign;

    private String nonceStr;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(String totalFee) {
        this.totalFee = totalFee;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getTimeEnd() {
        return timeEnd;
    }

    public void setTimeEnd(String timeEnd) {
        this.timeEnd = timeEnd;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getNonceStr() {
        return nonceStr;
    }

    public void setNonceStr(String nonceStr) {
        this.nonceStr = nonceStr;
    }
}
