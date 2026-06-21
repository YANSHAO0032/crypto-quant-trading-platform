package com.quant.oms;

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
    private final List<Strategy> strategies;

    /** 策略ID → 当前持仓订单数（P2-1：策略级持仓限额） */
    private final Map<String, AtomicInteger> strategyPositionCount = new ConcurrentHashMap<>();

    /** 每个策略最大同时持仓订单数，可外置配置 */
    private static final int MAX_POSITION_PER_STRATEGY = 3;

    /**
     * 根据信号下单，含策略级持仓限额检查。
     */
    public Order placeOrder(Signal signal, String symbol, BigDecimal price, BigDecimal quantity, String strategyId) {
        if (signal == Signal.HOLD) return null;

        // 策略级持仓限额
        AtomicInteger count = strategyPositionCount.computeIfAbsent(strategyId, k -> new AtomicInteger(0));
        if (count.get() >= MAX_POSITION_PER_STRATEGY) {
            log.warn("策略持仓已满: strategyId={}, current={}, max={}", strategyId, count.get(), MAX_POSITION_PER_STRATEGY);
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
        notifyOrderChange(created, strategyId, Strategy.OrderChangeType.NEW);

        boolean submitted = orderManager.submitOrder(created.getOrderId());
        if (!submitted) {
            log.warn("订单提交失败: orderId={}", created.getOrderId());
            notifyOrderChange(created, strategyId, Strategy.OrderChangeType.REJECTED);
            return null;
        }

        count.incrementAndGet();
        return created;
    }

    /**
     * 成交回调：持久化成交记录，分发 ENTER_FILL 事件，更新策略持仓计数。
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

            notifyOrderChange(order, order.getStrategyId(), Strategy.OrderChangeType.ENTER_FILL);
            log.info("订单成交更新: orderId={}, filledQty={}, avgPrice={}", orderId, filledQty, avgPrice);
        });
    }

    /**
     * 平仓成交回调：持久化并分发 EXIT_FILL 事件，释放策略持仓计数。
     */
    public void onExitFill(String orderId, BigDecimal filledQty, BigDecimal avgPrice) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setFilledQuantity(filledQty);
            order.setAvgFilledPrice(avgPrice);
            order.setStatus(OrderStatus.FILLED);
            order.setUpdateTime(LocalDateTime.now());
            orderRepository.save(order);

            String strategyId = order.getStrategyId();
            if (strategyId != null) {
                strategyPositionCount.computeIfPresent(strategyId, (k, c) -> {
                    c.decrementAndGet();
                    return c;
                });
            }

            notifyOrderChange(order, strategyId, Strategy.OrderChangeType.EXIT_FILL);
            log.info("平仓成交: orderId={}, filledQty={}, avgPrice={}", orderId, filledQty, avgPrice);
        });
    }

    private void notifyOrderChange(Order order, String strategyId, Strategy.OrderChangeType changeType) {
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
