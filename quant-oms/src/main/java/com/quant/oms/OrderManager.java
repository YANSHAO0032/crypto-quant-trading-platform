package com.quant.oms;

import com.quant.common.enums.OrderStatus;
import com.quant.common.model.Order;
import com.quant.risk.RiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 订单管理器
 * 负责订单的创建、提交、取消等核心业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManager {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final RiskEngine riskEngine;

    /**
     * 创建订单
     */
    public Order createOrder(Order order) {
        order.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        order.setStatus(OrderStatus.PENDING);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        orderRepository.save(order);
        log.info("订单已创建: orderId={}, symbol={}, side={}, price={}, qty={}",
                order.getOrderId(), order.getSymbol(), order.getSide(), order.getPrice(), order.getQuantity());
        return order;
    }

    /**
     * 提交订单（含风控检查）
     */
    public boolean submitOrder(String orderId) {
        Optional<Order> optOrder = orderRepository.findById(orderId);
        if (optOrder.isEmpty()) {
            log.warn("订单不存在: {}", orderId);
            return false;
        }

        Order order = optOrder.get();

        // 风控检查
        if (!riskEngine.check(order)) {
            stateMachine.transition(order, OrderStatus.REJECTED);
            order.setUpdateTime(LocalDateTime.now());
            order.setRemark("风控拒绝");
            orderRepository.save(order);
            return false;
        }

        // 状态转换
        if (!stateMachine.transition(order, OrderStatus.SUBMITTED)) {
            return false;
        }

        order.setUpdateTime(LocalDateTime.now());
        orderRepository.save(order);
        return true;
    }

    /**
     * 取消订单
     */
    public boolean cancelOrder(String orderId) {
        Optional<Order> optOrder = orderRepository.findById(orderId);
        if (optOrder.isEmpty()) {
            log.warn("订单不存在: {}", orderId);
            return false;
        }

        Order order = optOrder.get();
        if (!stateMachine.transition(order, OrderStatus.CANCELLED)) {
            return false;
        }

        order.setUpdateTime(LocalDateTime.now());
        orderRepository.save(order);
        return true;
    }

    /**
     * 查询订单
     */
    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * 查询策略相关订单
     */
    public List<Order> getOrdersByStrategy(String strategyId) {
        return orderRepository.findByStrategyId(strategyId);
    }

    /**
     * 查询所有订单
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
