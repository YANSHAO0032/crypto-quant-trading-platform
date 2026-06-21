package com.quant.oms;

import com.quant.common.enums.OrderSide;
import com.quant.common.enums.OrderStatus;
import com.quant.common.enums.OrderType;
import com.quant.common.enums.Signal;
import com.quant.common.model.Order;
import com.quant.common.model.Trade;
import com.quant.oms.mapper.TradeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单服务
 * 提供面向策略层的简化订单接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderManager orderManager;
    private final OrderRepository orderRepository;
    private final TradeMapper tradeMapper;

    /**
     * 根据信号下单
     */
    public Order placeOrder(Signal signal, String symbol, BigDecimal price, BigDecimal quantity, String strategyId) {
        if (signal == Signal.HOLD) {
            return null;
        }

        Order order = Order.builder()
                .symbol(symbol)
                .side(signal == Signal.BUY ? OrderSide.BUY : OrderSide.SELL)
                .type(OrderType.LIMIT)
                .price(price)
                .quantity(quantity)
                .filledQuantity(BigDecimal.ZERO)
                .strategyId(strategyId)
                .build();

        Order created = orderManager.createOrder(order);
        boolean submitted = orderManager.submitOrder(created.getOrderId());

        if (!submitted) {
            log.warn("订单提交失败: orderId={}", created.getOrderId());
            return null;
        }

        return created;
    }

    /**
     * 更新订单成交状态并持久化成交记录
     */
    public void onFill(String orderId, BigDecimal filledQty, BigDecimal avgPrice) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setFilledQuantity(filledQty);
            order.setAvgFilledPrice(avgPrice);
            order.setUpdateTime(LocalDateTime.now());
            if (filledQty.compareTo(order.getQuantity()) >= 0) {
                order.setStatus(OrderStatus.FILLED);
            } else {
                order.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
            orderRepository.save(order);

            Trade trade = Trade.builder()
                    .tradeId(UUID.randomUUID().toString().replace("-", ""))
                    .orderId(orderId)
                    .symbol(order.getSymbol())
                    .side(order.getSide())
                    .price(avgPrice)
                    .quantity(filledQty)
                    .tradeTime(LocalDateTime.now())
                    .build();
            tradeMapper.insert(trade);

            log.info("订单成交更新: orderId={}, filledQty={}, avgPrice={}", orderId, filledQty, avgPrice);
        });
    }
}
