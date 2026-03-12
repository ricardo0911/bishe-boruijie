package com.flowershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProcessSupportTicketRequest {

    @NotBlank(message = "处理状态不能为空")
    private String status;

    @Size(max = 2000, message = "处理备注不能超过2000个字符")
    private String handleNote;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHandleNote() {
        return handleNote;
    }

    public void setHandleNote(String handleNote) {
        this.handleNote = handleNote;
    }
}
