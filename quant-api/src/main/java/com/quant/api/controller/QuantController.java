package com.quant.api.controller;

import com.quant.api.vo.StrategyVO;
import com.quant.backtest.BacktestEngine;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.Order;
import com.quant.oms.IOrderManager;
import com.quant.oms.InOutOrderService;
import com.quant.risk.PositionManager;
import com.quant.risk.WalletManager;
import com.quant.strategy.Strategy;
import com.quant.strategy.StrategyRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quant")
@RequiredArgsConstructor
public class QuantController {

    private final IOrderManager orderManager;
    private final BacktestEngine backtestEngine;
    private final StrategyRunner strategyRunner;
    private final PositionManager positionManager;
    private final WalletManager walletManager;
    private final InOutOrderService inOutOrderService;

    // ========== 订单接口 ==========

    @GetMapping("/orders")
    public List<Order> listOrders() {
        return orderManager.getAllOrders();
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        return orderManager.getOrder(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
    public List<StrategyVO> listStrategies() {
        Map<String, String> running = strategyRunner.runningSnapshot();
        return strategyRunner.getAll().stream()
                .map(s -> StrategyVO.builder()
                        .strategyId(s.getStrategyId())
                        .strategyName(s.getStrategyName())
                        .running(strategyRunner.isRunning(s.getStrategyId()))
                        .symbol(running.get(s.getStrategyId()))
                        .build())
                .toList();
    }

    @PostMapping("/strategies/{strategyId}/start")
    public ResponseEntity<Map<String, Object>> startStrategy(
            @PathVariable String strategyId,
            @RequestParam String symbol) {

        if (strategyRunner.findById(strategyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (strategyRunner.isRunning(strategyId)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "策略已在运行中"));
        }
        strategyRunner.start(strategyId, symbol);
        return ResponseEntity.ok(Map.of("success", true, "strategyId", strategyId, "symbol", symbol));
    }

    @PostMapping("/strategies/{strategyId}/stop")
    public ResponseEntity<Map<String, Object>> stopStrategy(@PathVariable String strategyId) {
        if (strategyRunner.findById(strategyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!strategyRunner.isRunning(strategyId)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "策略未在运行"));
        }
        strategyRunner.stop(strategyId);
        return ResponseEntity.ok(Map.of("success", true, "strategyId", strategyId));
    }

    // ========== 仓位接口 ==========

    @GetMapping("/positions")
    public Map<String, BigDecimal> listPositions() {
        return positionManager.getAllPositions();
    }

    @GetMapping("/positions/{symbol}")
    public Map<String, Object> getPosition(@PathVariable String symbol) {
        BigDecimal qty = positionManager.getPosition(symbol);
        return Map.of("symbol", symbol, "quantity", qty);
    }

    // ========== 钱包接口 ==========

    @GetMapping("/wallet")
    public Map<String, BigDecimal[]> getWallet() {
        return walletManager.snapshot();
    }

    @GetMapping("/wallet/{asset}")
    public Map<String, Object> getAssetBalance(@PathVariable String asset) {
        return Map.of(
                "asset", asset.toUpperCase(),
                "available", walletManager.getAvailable(asset),
                "frozen", walletManager.getFrozen(asset),
                "total", walletManager.getTotal(asset)
        );
    }

    // ========== InOutOrder 接口 ==========

    @GetMapping("/trades/{strategyId}/open")
    public Object getOpenTrades(@PathVariable String strategyId) {
        return inOutOrderService.getOpenRecords(strategyId);
    }

    @GetMapping("/trades/{strategyId}/closed")
    public Object getClosedTrades(@PathVariable String strategyId) {
        return inOutOrderService.getClosedRecords(strategyId);
    }

    @GetMapping("/trades/{strategyId}/pnl")
    public Map<String, Object> getTotalPnl(@PathVariable String strategyId) {
        return Map.of("strategyId", strategyId,
                "totalRealizedPnl", inOutOrderService.getTotalRealizedPnl(strategyId));
    }

    // ========== 回测接口 ==========

    /**
     * 基于 kline_{symbol} 分表的标准回测（推荐）。
     * startMs/endMs 传 Unix 毫秒时间戳，如 1672502400000 = 2023-01-01 00:00:00 UTC
     */
    @PostMapping("/backtest/{strategyId}/kline")
    public ResponseEntity<BacktestReport> runKlineBacktest(
            @PathVariable String strategyId,
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam long startMs,
            @RequestParam long endMs,
            @RequestParam(defaultValue = "100000") BigDecimal capital) {

        Strategy strategy = strategyRunner.findById(strategyId).orElse(null);
        if (strategy == null) return ResponseEntity.notFound().build();
        if (strategyRunner.isRunning(strategyId)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                backtestEngine.runKlineBacktest(strategy, symbol, interval, startMs, endMs, capital));
    }

    /**
     * 基于 kline_{symbol} 分表的快速回测（取最新 N 根，无需指定时间范围）。
     */
    @PostMapping("/backtest/{strategyId}/kline/latest")
    public ResponseEntity<BacktestReport> runLatestKlineBacktest(
            @PathVariable String strategyId,
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(defaultValue = "100000") BigDecimal capital) {

        Strategy strategy = strategyRunner.findById(strategyId).orElse(null);
        if (strategy == null) return ResponseEntity.notFound().build();
        if (strategyRunner.isRunning(strategyId)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                backtestEngine.quickKlineBacktest(strategy, symbol, interval, limit, capital));
    }

    @PostMapping("/backtest/{strategyId}")
    public ResponseEntity<BacktestReport> runBacktest(
            @PathVariable String strategyId,
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1000") int dataCount,
            @RequestParam(defaultValue = "65000") BigDecimal startPrice,
            @RequestParam(defaultValue = "100000") BigDecimal capital) {

        Strategy strategy = strategyRunner.findById(strategyId).orElse(null);
        if (strategy == null) {
            return ResponseEntity.notFound().build();
        }
        if (strategyRunner.isRunning(strategyId)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(backtestEngine.quickBacktest(strategy, symbol, dataCount, startPrice, capital));
    }

    @GetMapping("/backtest")
    public List<BacktestReport> listBacktestReports(
            @RequestParam(required = false) String strategyId,
            @RequestParam(defaultValue = "20") int limit) {
        return backtestEngine.listReports(strategyId, limit);
    }
}
