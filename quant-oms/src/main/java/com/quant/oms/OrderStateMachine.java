package com.quant.oms;

import com.quant.common.enums.OrderStatus;
import com.quant.common.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机
 * 管理订单状态的合法转换
 */
@Slf4j
@Component
public class OrderStateMachine {

    /** 状态转换规则 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.SUBMITTED, OrderStatus.REJECTED, OrderStatus.CANCELLED),
            OrderStatus.SUBMITTED, Set.of(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.EXPIRED),
            OrderStatus.PARTIALLY_FILLED, Set.of(OrderStatus.FILLED, OrderStatus.CANCELLED)
    );

    /**
     * 尝试状态转换
     * @param order 订单
     * @param targetStatus 目标状态
     * @return true=转换成功
     */
    public boolean transition(Order order, OrderStatus targetStatus) {
        OrderStatus currentStatus = order.getStatus();
        Set<OrderStatus> allowedTargets = TRANSITIONS.get(currentStatus);

        if (allowedTargets == null || !allowedTargets.contains(targetStatus)) {
            log.warn("非法状态转换: orderId={}, {} -> {}", order.getOrderId(), currentStatus, targetStatus);
            return false;
        }

        order.setStatus(targetStatus);
        log.info("订单状态变更: orderId={}, {} -> {}", order.getOrderId(), currentStatus, targetStatus);
        return true;
    }

    /**
     * 判断订单是否为终态
     */
    public boolean isFinalState(OrderStatus status) {
        return status == OrderStatus.FILLED
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.REJECTED
                || status == OrderStatus.EXPIRED;
    }
}
