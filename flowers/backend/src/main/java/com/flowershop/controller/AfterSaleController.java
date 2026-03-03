package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.AuditRefundRequest;
import com.flowershop.dto.ProcessRefundRequest;
import com.flowershop.dto.RefundRequest;
import com.flowershop.dto.RefundResponse;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.rbac.Permission;
import com.flowershop.rbac.RequirePermission;
import com.flowershop.rbac.RequireRole;
import com.flowershop.rbac.Role;
import com.flowershop.service.AfterSaleService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 售后管理控制器
 * 处理退款申请、审核、退款处理流程
 */
@RestController
@RequestMapping("/api/v1/after-sales")
@RequireRole({Role.MERCHANT, Role.SUPER_ADMIN})
public class AfterSaleController {

    private static final Logger logger = LoggerFactory.getLogger(AfterSaleController.class);

    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    /**
     * 申请售后（退款）
     * 用户提交退款申请，订单状态变为 REFUND_REQUESTED
     *
     * @param request 退款申请请求
     * @return 退款记录
     */
    @PostMapping
    public ApiResponse<RefundResponse> applyRefund(@Valid @RequestBody RefundRequest request) {
        logger.info("收到退款申请请求, orderNo: {}, amount: {}", request.getOrderNo(), request.getRefundAmount());
        try {
            RefundResponse response = afterSaleService.applyRefund(request);
            logger.info("退款申请处理成功, orderNo: {}, refundNo: {}", request.getOrderNo(), response.getRefundNo());
            return ApiResponse.success("退款申请提交成功", response);
        } catch (Exception e) {
            logger.error("退款申请处理失败, orderNo: {}", request.getOrderNo(), e);
            throw e;
        }
    }

    /**
     * 查询售后记录
     * 根据订单号查询售后记录
     *
     * @param orderNo 订单号
     * @return 售后记录
     */
    @GetMapping("/{orderNo}")
    public ApiResponse<RefundResponse> getAfterSaleByOrderNo(@PathVariable String orderNo) {
        logger.info("查询售后记录, orderNo: {}", orderNo);
        RefundResponse response = afterSaleService.getAfterSaleByOrderNo(orderNo);
        if (response == null) {
            return ApiResponse.success("该订单暂无售后记录", null);
        }
        return ApiResponse.success(response);
    }

    /**
     * 审核通过
     * 商家审核通过退款申请，状态变为 REFUNDING
     *
     * @param id 售后记录ID
     * @return 更新后的退款记录
     */
    @RequirePermission(Permission.ORDER_UPDATE)
    @PostMapping("/{id}/approve")
    public ApiResponse<RefundResponse> approveRefund(@PathVariable Long id) {
        logger.info("审核通过退款申请, afterSaleId: {}", id);
        try {
            RefundResponse response = afterSaleService.approveRefund(id);
            logger.info("退款审核通过成功, afterSaleId: {}, refundNo: {}", id, response.getRefundNo());
            return ApiResponse.success("退款审核通过", response);
        } catch (Exception e) {
            logger.error("退款审核通过失败, afterSaleId: {}", id, e);
            throw e;
        }
    }

    /**
     * 审核拒绝
     * 商家审核拒绝退款申请，订单回到原状态
     *
     * @param id 售后记录ID
     * @param request 拒绝原因
     * @return 更新后的退款记录
     */
    @RequirePermission(Permission.ORDER_UPDATE)
    @PostMapping("/{id}/reject")
    public ApiResponse<RefundResponse> rejectRefund(
            @PathVariable Long id,
            @RequestBody(required = false) AuditRefundRequest request) {
        logger.info("审核拒绝退款申请, afterSaleId: {}", id);
        try {
            RefundResponse response = afterSaleService.rejectRefund(id, request);
            logger.info("退款审核拒绝成功, afterSaleId: {}, refundNo: {}", id, response.getRefundNo());
            return ApiResponse.success("退款审核已拒绝", response);
        } catch (Exception e) {
            logger.error("退款审核拒绝失败, afterSaleId: {}", id, e);
            throw e;
        }
    }

    /**
     * 执行退款
     * 调用微信支付接口执行退款，成功后回补库存
     *
     * @param id 售后记录ID
     * @param request 退款处理请求
     * @return 处理结果
     */
    @RequirePermission(Permission.ORDER_UPDATE)
    @PostMapping("/{id}/process")
    public ApiResponse<SimpleActionResponse> processRefund(
            @PathVariable Long id,
            @Valid @RequestBody ProcessRefundRequest request) {
        logger.info("执行退款处理, afterSaleId: {}, refundId: {}", id, request.getRefundId());
        try {
            SimpleActionResponse response = afterSaleService.processRefund(id, request);
            logger.info("退款处理完成, afterSaleId: {}, status: {}", id, response.status());
            return ApiResponse.success("退款处理完成", response);
        } catch (Exception e) {
            logger.error("退款处理失败, afterSaleId: {}", id, e);
            throw e;
        }
    }

    /**
     * 查询售后记录列表（商家端）
     *
     * @param status 状态筛选
     * @param limit 数量限制
     * @return 售后记录列表
     */
    @GetMapping("/list")
    public ApiResponse<List<RefundResponse>> listAfterSales(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") Integer limit) {
        logger.info("查询售后记录列表, status: {}, limit: {}", status, limit);
        List<RefundResponse> list = afterSaleService.listAfterSales(status, limit);
        return ApiResponse.success(list);
    }
}
