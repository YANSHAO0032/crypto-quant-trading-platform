package com.quant.execution;

import com.quant.common.enums.OrderStatus;
import com.quant.common.model.Order;
import com.quant.oms.OrderManager;
import com.quant.oms.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 执行服务
 * 负责将OMS订单发送到交易所执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExchangeClient exchangeClient;
    private final OrderManager orderManager;
    private final OrderRepository orderRepository;

    /**
     * 执行订单
     */
    public void executeOrder(String orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            if (order.getStatus() != OrderStatus.SUBMITTED) {
                log.warn("订单状态非SUBMITTED，无法执行: orderId={}, status={}", orderId, order.getStatus());
                return;
            }

            try {
                String exchangeOrderId = exchangeClient.sendOrder(order);
                order.setExchangeOrderId(exchangeOrderId);
                order.setUpdateTime(LocalDateTime.now());
                log.info("订单已发送到交易所: orderId={}, exchangeOrderId={}", orderId, exchangeOrderId);
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
     * 撤销交易所订单
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
