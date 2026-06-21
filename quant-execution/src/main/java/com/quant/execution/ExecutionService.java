package com.quant.execution;

import com.quant.common.enums.OrderStatus;
import com.quant.common.model.Order;
import com.quant.oms.OrderManager;
import com.quant.oms.OrderRepository;
import com.quant.oms.event.OrderSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 执行服务：监听 OrderSubmittedEvent，将订单发往交易所。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExchangeClient exchangeClient;
    private final OrderManager orderManager;
    private final OrderRepository orderRepository;

    /**
     * 监听订单提交事件，异步将订单发往交易所。
     */
    @Async
    @EventListener
    public void onOrderSubmitted(OrderSubmittedEvent event) {
        executeOrder(event.getOrderId());
    }

    /**
     * 将订单发送到交易所执行。
     */
    public void executeOrder(String orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() != OrderStatus.SUBMITTED) {
                log.warn("订单状态非SUBMITTED，无法执行: orderId={}, status={}", orderId, order.getStatus());
                return;
            }

            try {
                String exchangeOrderId = exchangeClient.sendOrder(order);
                if (exchangeOrderId == null) {
                    order.setStatus(OrderStatus.REJECTED);
                    order.setRemark("交易所拒绝");
                } else {
                    order.setExchangeOrderId(exchangeOrderId);
                }
                order.setUpdateTime(LocalDateTime.now());
            } catch (Exception e) {
                log.error("订单执行异常: orderId={}", orderId, e);
                order.setStatus(OrderStatus.REJECTED);
                order.setRemark("执行异常: " + e.getMessage());
                order.setUpdateTime(LocalDateTime.now());
            }
            orderRepository.save(order);
        }, () -> log.warn("订单不存在: {}", orderId));
    }

    /**
     * 撤销交易所订单。
     */
    public boolean cancelExchangeOrder(String orderId) {
        return orderManager.getOrder(orderId).map(order -> {
            if (order.getExchangeOrderId() == null) {
                log.warn("订单未发送到交易所: orderId={}", orderId);
                return false;
            }
            boolean result = exchangeClient.cancelOrder(order.getSymbol(), order.getExchangeOrderId());
            if (result) {
                orderManager.cancelOrder(orderId);
            }
            return result;
        }).orElse(false);
    }
}
