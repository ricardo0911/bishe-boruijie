package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.PaymentCallbackRequest;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付回调控制器
 * 处理微信支付异步通知
 */
@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 微信支付回调接口
     * 微信服务器会异步调用此接口通知支付结果
     *
     * @param request 回调请求参数
     * @return 处理结果
     */
    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SimpleActionResponse> paymentCallback(
            @Valid @RequestBody PaymentCallbackRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        logger.info("收到支付回调请求, orderNo: {}, clientIp: {}, transactionId: {}",
                request.getOrderNo(), clientIp, request.getTransactionId());

        try {
            SimpleActionResponse response = paymentService.handlePaymentCallback(request);
            logger.info("支付回调处理成功, orderNo: {}, status: {}",
                    request.getOrderNo(), response.status());
            return ApiResponse.success("支付回调处理成功", response);
        } catch (Exception e) {
            logger.error("支付回调处理失败, orderNo: {}", request.getOrderNo(), e);
            throw e;
        }
    }

    /**
     * 微信支付回调接口（XML格式，兼容微信原生通知）
     * 微信官方SDK可能发送XML格式通知
     */
    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_XML_VALUE)
    public ApiResponse<SimpleActionResponse> paymentCallbackXml(
            @RequestBody Map<String, String> xmlParams,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        logger.info("收到支付回调请求(XML), orderNo: {}, clientIp: {}",
                xmlParams.get("orderNo"), clientIp);

        // 将XML参数转换为DTO
        PaymentCallbackRequest request = convertToDto(xmlParams);

        try {
            SimpleActionResponse response = paymentService.handlePaymentCallback(request);
            logger.info("支付回调处理成功(XML), orderNo: {}, status: {}",
                    request.getOrderNo(), response.status());
            return ApiResponse.success("支付回调处理成功", response);
        } catch (Exception e) {
            logger.error("支付回调处理失败(XML), orderNo: {}", request.getOrderNo(), e);
            throw e;
        }
    }

    /**
     * 查询支付状态
     * 供前端轮询支付结果
     *
     * @param orderNo 订单号
     * @return 支付状态
     */
    @GetMapping("/status/{orderNo}")
    public ApiResponse<Map<String, Object>> queryPaymentStatus(@PathVariable String orderNo) {
        // 实际实现可以查询订单状态并返回
        // 这里简化处理，返回成功响应
        return ApiResponse.success(Map.of(
            "orderNo", orderNo,
            "status", "PENDING",
            "message", "请调用订单详情接口查询支付状态"
        ));
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 将XML参数转换为DTO
     */
    private PaymentCallbackRequest convertToDto(Map<String, String> params) {
        PaymentCallbackRequest dto = new PaymentCallbackRequest();
        dto.setOrderNo(params.get("out_trade_no"));
        dto.setTransactionId(params.get("transaction_id"));
        dto.setTotalFee(params.get("total_fee"));
        dto.setResultCode(params.get("result_code"));
        dto.setTimeEnd(params.get("time_end"));
        dto.setSign(params.get("sign"));
        dto.setNonceStr(params.get("nonce_str"));
        return dto;
    }
}
