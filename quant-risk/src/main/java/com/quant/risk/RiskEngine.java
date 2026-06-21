package com.quant.risk;

import com.quant.common.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 风控引擎
 * 对订单进行风控检查，决定是否放行
 */
@Slf4j
@Component
public class RiskEngine {

    private final RiskChecker riskChecker;
    private final PositionManager positionManager;

    public RiskEngine(RiskChecker riskChecker, PositionManager positionManager) {
        this.riskChecker = riskChecker;
        this.positionManager = positionManager;
    }

    /**
     * 风控检查入口
     * @param order 待检查订单
     * @return true=通过, false=拒绝
     */
    public boolean check(Order order) {
        // 1. 基础参数校验
        if (!riskChecker.checkOrderParams(order)) {
            log.warn("风控拒绝: 订单参数异常 orderId={}", order.getOrderId());
            return false;
        }

        // 2. 仓位检查
        BigDecimal currentPosition = positionManager.getPosition(order.getSymbol());
        if (!riskChecker.checkPositionLimit(order, currentPosition)) {
            log.warn("风控拒绝: 超出仓位限制 orderId={}, currentPos={}", order.getOrderId(), currentPosition);
            return false;
        }

        // 3. 频率检查
        if (!riskChecker.checkOrderFrequency(order)) {
            log.warn("风控拒绝: 下单频率过高 orderId={}", order.getOrderId());
            return false;
        }

        // 4. 单笔金额检查
        if (!riskChecker.checkOrderAmount(order)) {
            log.warn("风控拒绝: 单笔金额超限 orderId={}", order.getOrderId());
            return false;
        }

        log.info("风控通过: orderId={}", order.getOrderId());
        return true;
    }
}
