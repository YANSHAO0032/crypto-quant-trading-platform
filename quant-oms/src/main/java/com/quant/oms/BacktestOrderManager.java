package com.quant.oms;

import com.quant.common.enums.OrderStatus;
import com.quant.common.model.Order;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回测专用订单管理器：纯内存模拟撮合，不走数据库和交易所。
 * 与 OrderManager（实盘）共享 IOrderManager 接口，策略代码无需感知环境差异。
 * 对应 banbot odmgr_local.go 设计。
 */
@Slf4j
public class BacktestOrderManager implements IOrderManager {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    @Override
    public Order createOrder(Order order) {
        order.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        order.setStatus(OrderStatus.PENDING);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orders.put(order.getOrderId(), order);
        log.debug("[回测] 订单创建: orderId={}, symbol={}, side={}, price={}",
                order.getOrderId(), order.getSymbol(), order.getSide(), order.getPrice());
        return order;
    }

    @Override
    public boolean submitOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) return false;
        order.setStatus(OrderStatus.SUBMITTED);
        order.setUpdateTime(LocalDateTime.now());
        // 回测场景：立即模拟成交（SUBMITTED → FILLED）
        order.setFilledQuantity(order.getQuantity());
        order.setAvgFilledPrice(order.getPrice());
        order.setStatus(OrderStatus.FILLED);
        log.debug("[回测] 订单撮合成交: orderId={}, price={}", orderId, order.getPrice());
        return true;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) return false;
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdateTime(LocalDateTime.now());
        return true;
    }

    @Override
    public Optional<Order> getOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<Order> getOrdersByStrategy(String strategyId) {
        return orders.values().stream()
                .filter(o -> strategyId.equals(o.getStrategyId()))
                .toList();
    }

    @Override
    public List<Order> getAllOrders() {
        return new ArrayList<>(orders.values());
    }

    /** 回测结束后清空状态，便于多次回测复用同一实例。 */
    public void reset() {
        orders.clear();
    }
}
