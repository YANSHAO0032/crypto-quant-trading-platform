package com.quant.api.controller;

import com.quant.backtest.BacktestEngine;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.Order;
import com.quant.oms.OrderManager;
import com.quant.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 量化交易REST接口
 */
@RestController
@RequestMapping("/api/quant")
@RequiredArgsConstructor
public class QuantController {

    private final OrderManager orderManager;
    private final BacktestEngine backtestEngine;
    private final List<Strategy> strategies;

    // ========== 订单接口 ==========

    @GetMapping("/orders")
    public List<Order> listOrders() {
        return orderManager.getAllOrders();
    }

    @GetMapping("/orders/{orderId}")
    public Order getOrder(@PathVariable String orderId) {
        return orderManager.getOrder(orderId).orElse(null);
    }

    @PostMapping("/orders")
    public Order createOrder(@RequestBody Order order) {
        return orderManager.createOrder(order);
    }

    @PostMapping("/orders/{orderId}/submit")
    public Map<String, Object> submitOrder(@PathVariable String orderId) {
        boolean result = orderManager.submitOrder(orderId);
        return Map.of("success", result, "orderId", orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable String orderId) {
        boolean result = orderManager.cancelOrder(orderId);
        return Map.of("success", result, "orderId", orderId);
    }

    // ========== 策略接口 ==========

    @GetMapping("/strategies")
    public List<Map<String, Object>> listStrategies() {
        return strategies.stream().map(s -> Map.<String, Object>of(
                "strategyId", s.getStrategyId(),
                "strategyName", s.getStrategyName(),
                "running", s.isRunning()
        )).toList();
    }

    // ========== 回测接口 ==========

    @PostMapping("/backtest/{strategyId}")
    public BacktestReport runBacktest(
            @PathVariable String strategyId,
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1000") int dataCount,
            @RequestParam(defaultValue = "65000") BigDecimal startPrice,
            @RequestParam(defaultValue = "100000") BigDecimal capital) {

        Strategy strategy = strategies.stream()
                .filter(s -> s.getStrategyId().equals(strategyId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("策略不存在: " + strategyId));

        return backtestEngine.quickBacktest(strategy, symbol, dataCount, startPrice, capital);
    }
}
