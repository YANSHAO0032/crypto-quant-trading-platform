package com.quant.oms;

import com.quant.common.enums.OrderChangeType;
import com.quant.common.enums.OrderSide;
import com.quant.common.enums.OrderStatus;
import com.quant.common.enums.OrderType;
import com.quant.common.enums.Signal;
import com.quant.common.model.Order;
import com.quant.common.model.Trade;
import com.quant.oms.mapper.TradeMapper;
import com.quant.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单服务：面向策略的下单/成交接口，并分发 onOrderChange 事件给对应策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderManager orderManager;
    private final OrderRepository orderRepository;
    private final TradeMapper tradeMapper;
    private final InOutOrderService inOutOrderService;
    private final List<Strategy> strategies;

    @Value("${order.max-position-per-strategy:3}")
    private int maxPositionPerStrategy;

    /** 策略ID → 当前持仓订单数 */
    private final Map<String, AtomicInteger> strategyPositionCount = new ConcurrentHashMap<>();

    public Order placeOrder(Signal signal, String symbol, BigDecimal price, BigDecimal quantity, String strategyId) {
        if (signal == Signal.HOLD) return null;

        AtomicInteger count = strategyPositionCount.computeIfAbsent(strategyId, k -> new AtomicInteger(0));
        if (count.get() >= maxPositionPerStrategy) {
            log.warn("策略持仓已满: strategyId={}, current={}, max={}", strategyId, count.get(), maxPositionPerStrategy);
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
        notifyOrderChange(created, strategyId, OrderChangeType.NEW);

        boolean submitted = orderManager.submitOrder(created.getOrderId());
        if (!submitted) {
            log.warn("订单提交失败: orderId={}", created.getOrderId());
            notifyOrderChange(created, strategyId, OrderChangeType.REJECTED);
            return null;
        }

        count.incrementAndGet();
        return created;
    }

    /**
     * 开仓成交回调：更新订单状态、插入成交记录、创建 InOutOrder、通知策略。
     */
    public void onFill(String orderId, BigDecimal filledQty, BigDecimal avgPrice) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setFilledQuantity(filledQty);
            order.setAvgFilledPrice(avgPrice);
            order.setUpdateTime(LocalDateTime.now());
            order.setStatus(filledQty.compareTo(order.getQuantity()) >= 0
                    ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
            orderRepository.save(order);

            insertTradeRecord(order, filledQty, avgPrice);

            // 创建 InOutOrder 开仓记录
            inOutOrderService.recordOpen(order);

            notifyOrderChange(order, order.getStrategyId(), OrderChangeType.ENTER_FILL);
            log.info("开仓成交: orderId={}, filledQty={}, avgPrice={}", orderId, filledQty, avgPrice);
        });
    }

    /**
     * 平仓成交回调：更新订单状态、插入成交记录、关闭 InOutOrder、通知策略。
     */
    public void onExitFill(String orderId, BigDecimal filledQty, BigDecimal avgPrice) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setFilledQuantity(filledQty);
            order.setAvgFilledPrice(avgPrice);
            order.setStatus(OrderStatus.FILLED);
            order.setUpdateTime(LocalDateTime.now());
            orderRepository.save(order);

            insertTradeRecord(order, filledQty, avgPrice);

            // 关闭 InOutOrder 平仓记录，计算盈亏
            inOutOrderService.recordClose(order);

            String strategyId = order.getStrategyId();
            if (strategyId != null) {
                strategyPositionCount.computeIfPresent(strategyId, (k, c) -> {
                    c.decrementAndGet();
                    return c;
                });
            }

            notifyOrderChange(order, strategyId, OrderChangeType.EXIT_FILL);
            log.info("平仓成交: orderId={}, filledQty={}, avgPrice={}", orderId, filledQty, avgPrice);
        });
    }

    private void insertTradeRecord(Order order, BigDecimal filledQty, BigDecimal avgPrice) {
        tradeMapper.insert(Trade.builder()
                .tradeId(UUID.randomUUID().toString().replace("-", ""))
                .orderId(order.getOrderId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .price(avgPrice)
                .quantity(filledQty)
                .tradeTime(LocalDateTime.now())
                .build());
    }

    private void notifyOrderChange(Order order, String strategyId, OrderChangeType changeType) {
        if (strategyId == null) return;
        strategies.stream()
                .filter(s -> strategyId.equals(s.getStrategyId()))
                .findFirst()
                .ifPresent(s -> {
                    try {
                        s.onOrderChange(order, changeType);
                    } catch (Exception e) {
                        log.error("策略 onOrderChange 回调异常: strategyId={}, changeType={}", strategyId, changeType, e);
                    }
                });
    }
}
