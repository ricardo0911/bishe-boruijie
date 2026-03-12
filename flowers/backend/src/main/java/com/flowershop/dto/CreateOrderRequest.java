package com.flowershop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderRequest {

    @NotNull(message = "userId不能为空")
    private Long userId;

    @NotEmpty(message = "items不能为空")
    @Valid
    private List<OrderLine> items = new ArrayList<>();

    @DecimalMin(value = "0.00", message = "packagingFee不能小于0")
    private BigDecimal packagingFee = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "deliveryFee不能小于0")
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    private String deliveryMode;
    private String deliverySlot;
    private String remark;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<OrderLine> getItems() {
        return items;
    }

    public void setItems(List<OrderLine> items) {
        this.items = items;
    }

    public BigDecimal getPackagingFee() {
        return packagingFee == null ? BigDecimal.ZERO : packagingFee;
    }

    public void setPackagingFee(BigDecimal packagingFee) {
        this.packagingFee = packagingFee;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee == null ? BigDecimal.ZERO : deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getDeliverySlot() {
        return deliverySlot;
    }

    public void setDeliverySlot(String deliverySlot) {
        this.deliverySlot = deliverySlot;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public static class OrderLine {

        @NotNull(message = "productId不能为空")
        private Long productId;

        @NotNull(message = "quantity不能为空")
        @Min(value = 1, message = "quantity不能小于1")
        private Integer quantity;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
