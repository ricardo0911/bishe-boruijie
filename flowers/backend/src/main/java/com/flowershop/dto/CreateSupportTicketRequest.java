package com.flowershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateSupportTicketRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Size(max = 64, message = "订单号不能超过64个字符")
    private String orderNo;

    @NotBlank(message = "问题类型不能为空")
    @Size(max = 32, message = "问题类型不能超过32个字符")
    private String issueType;

    @NotBlank(message = "问题标题不能为空")
    @Size(max = 120, message = "问题标题不能超过120个字符")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 2000, message = "问题描述不能超过2000个字符")
    private String content;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 64, message = "联系人不能超过64个字符")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }
}
