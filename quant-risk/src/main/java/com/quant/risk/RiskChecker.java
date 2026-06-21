package com.quant.risk;

import com.quant.common.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 风控检查器
 * 提供各类风控规则检查
 */
@Slf4j
@Component
public class RiskChecker {

    @Value("${risk.max-order-amount:100000}")
    private BigDecimal maxOrderAmount;

    @Value("${risk.max-position:10}")
    private BigDecimal maxPosition;

    @Value("${risk.max-order-per-minute:60}")
    private int maxOrderPerMinute;

    /** 策略ID -> 最近1分钟内下单计数 */
    private final Map<String, AtomicInteger> orderCountMap = new ConcurrentHashMap<>();

    public boolean checkOrderParams(Order order) {
        if (order == null) return false;
        if (order.getSymbol() == null || order.getSymbol().isEmpty()) return false;
        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) return false;
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) return false;
        return order.getSide() != null && order.getType() != null;
    }

    public boolean checkPositionLimit(Order order, BigDecimal currentPosition) {
        BigDecimal afterPosition = currentPosition.add(order.getQuantity());
        return afterPosition.abs().compareTo(maxPosition) <= 0;
    }

    public boolean checkOrderFrequency(Order order) {
        String key = order.getStrategyId() != null ? order.getStrategyId() : "default";
        AtomicInteger count = orderCountMap.computeIfAbsent(key, k -> new AtomicInteger(0));
        return count.incrementAndGet() <= maxOrderPerMinute;
    }

    public boolean checkOrderAmount(Order order) {
        BigDecimal amount = order.getPrice().multiply(order.getQuantity());
        return amount.compareTo(maxOrderAmount) <= 0;
    }

    @Scheduled(fixedRate = 60_000)
    public void resetOrderCount() {
        orderCountMap.clear();
        log.debug("风控频率计数已重置");
    }
}
