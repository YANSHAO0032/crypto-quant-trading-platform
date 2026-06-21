package com.quant.oms;

import com.quant.common.enums.OrderStatus;
import com.quant.common.model.Order;
import com.quant.oms.event.OrderSubmittedEvent;
import com.quant.risk.RiskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 实盘订单管理器：实现 IOrderManager，走真实风控与数据库持久化。
 * 提交成功后发布 OrderSubmittedEvent，由 ExecutionService 监听并发往交易所。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManager implements IOrderManager {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final RiskEngine riskEngine;
    private final ApplicationEventPublisher eventPublisher;

    @Override
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

    @Override
    public boolean submitOrder(String orderId) {
        Optional<Order> optOrder = orderRepository.findById(orderId);
        if (optOrder.isEmpty()) {
            log.warn("订单不存在: {}", orderId);
            return false;
        }

        Order order = optOrder.get();

        if (!riskEngine.check(order)) {
            stateMachine.transition(order, OrderStatus.REJECTED);
            order.setUpdateTime(LocalDateTime.now());
            order.setRemark("风控拒绝");
            orderRepository.save(order);
            return false;
        }

        riskEngine.freezeFunds(order);

        if (!stateMachine.transition(order, OrderStatus.SUBMITTED)) {
            riskEngine.unfreezeFunds(order);
            return false;
        }

        order.setUpdateTime(LocalDateTime.now());
        orderRepository.save(order);

        // 发布事件，ExecutionService 监听后将订单发往交易所
        eventPublisher.publishEvent(new OrderSubmittedEvent(this, orderId));
        return true;
    }

    @Override
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

        riskEngine.unfreezeFunds(order);
        order.setUpdateTime(LocalDateTime.now());
        orderRepository.save(order);
        return true;
    }

    @Override
    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    public List<Order> getOrdersByStrategy(String strategyId) {
        return orderRepository.findByStrategyId(strategyId);
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
