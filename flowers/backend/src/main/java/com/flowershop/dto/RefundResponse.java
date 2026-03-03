package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款记录响应DTO
 */
public class RefundResponse {

    private Long id;
    private String refundNo;
    private String orderNo;
    private Long orderId;
    private BigDecimal refundAmount;
    private String reason;
    private String description;
    private String evidenceImages;
    private String status;
    private String rejectReason;
    private String transactionId;
    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
    private LocalDateTime refundTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RefundResponse() {
    }

    public RefundResponse(Long id, String refundNo, String orderNo, Long orderId,
                          BigDecimal refundAmount, String reason, String description,
                          String evidenceImages, String status, String rejectReason,
                          String transactionId, LocalDateTime applyTime, LocalDateTime auditTime,
                          LocalDateTime refundTime, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.refundNo = refundNo;
        this.orderNo = orderNo;
        this.orderId = orderId;
        this.refundAmount = refundAmount;
        this.reason = reason;
        this.description = description;
        this.evidenceImages = evidenceImages;
        this.status = status;
        this.rejectReason = rejectReason;
        this.transactionId = transactionId;
        this.applyTime = applyTime;
        this.auditTime = auditTime;
        this.refundTime = refundTime;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEvidenceImages() {
        return evidenceImages;
    }

    public void setEvidenceImages(String evidenceImages) {
        this.evidenceImages = evidenceImages;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public LocalDateTime getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(LocalDateTime applyTime) {
        this.applyTime = applyTime;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public LocalDateTime getRefundTime() {
        return refundTime;
    }

    public void setRefundTime(LocalDateTime refundTime) {
        this.refundTime = refundTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
