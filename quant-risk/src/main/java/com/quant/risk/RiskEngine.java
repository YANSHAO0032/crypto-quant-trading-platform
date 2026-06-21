package com.quant.risk;

import com.quant.common.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 风控引擎：对订单进行多层校验，决定是否放行
 */
@Slf4j
@Component
public class RiskEngine {

    private final RiskChecker riskChecker;
    private final PositionManager positionManager;
    private final WalletManager walletManager;

    public RiskEngine(RiskChecker riskChecker, PositionManager positionManager, WalletManager walletManager) {
        this.riskChecker = riskChecker;
        this.positionManager = positionManager;
        this.walletManager = walletManager;
    }

    /**
     * 风控检查入口
     * @return true=通过, false=拒绝
     */
    public boolean check(Order order) {
        if (!riskChecker.checkOrderParams(order)) {
            log.warn("风控拒绝: 订单参数异常 orderId={}", order.getOrderId());
            return false;
        }

        BigDecimal currentPosition = positionManager.getPosition(order.getSymbol());
        if (!riskChecker.checkPositionLimit(order, currentPosition)) {
            log.warn("风控拒绝: 超出仓位限制 orderId={}, currentPos={}", order.getOrderId(), currentPosition);
            return false;
        }

        if (!riskChecker.checkOrderFrequency(order)) {
            log.warn("风控拒绝: 下单频率过高 orderId={}", order.getOrderId());
            return false;
        }

        if (!riskChecker.checkOrderAmount(order)) {
            log.warn("风控拒绝: 单笔金额超限 orderId={}", order.getOrderId());
            return false;
        }

        // 5. 资金可用性校验（买单需校验 USDT 余额）
        BigDecimal orderValue = order.getPrice().multiply(order.getQuantity());
        if (!walletManager.hasEnough("USDT", orderValue)) {
            log.warn("风控拒绝: USDT余额不足 orderId={}, required={}, available={}",
                    order.getOrderId(), orderValue, walletManager.getAvailable("USDT"));
            return false;
        }

        log.info("风控通过: orderId={}", order.getOrderId());
        return true;
    }

    /**
     * 下单冻结资金（风控通过后调用）。
     */
    public boolean freezeFunds(Order order) {
        BigDecimal orderValue = order.getPrice().multiply(order.getQuantity());
        return walletManager.freeze("USDT", orderValue);
    }

    /**
     * 撤单解冻资金。
     */
    public void unfreezeFunds(Order order) {
        BigDecimal orderValue = order.getPrice().multiply(order.getQuantity());
        walletManager.unfreeze("USDT", orderValue);
    }
}

