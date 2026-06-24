package com.quant.api.controller;

import com.quant.api.request.CreateOrderRequest;
import com.quant.api.request.KlineBacktestRequest;
import com.quant.api.request.LatestKlineBacktestRequest;
import com.quant.api.request.QuickBacktestRequest;
import com.quant.api.request.StartStrategyRequest;
import com.quant.api.vo.StrategyVO;
import com.quant.backtest.BacktestConfig;
import com.quant.backtest.BacktestEngine;
import com.quant.backtest.PositionSizingMode;
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
    public Order createOrder(@RequestBody CreateOrderRequest request) {
        return orderManager.createOrder(Order.builder()
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(request.getType())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .strategyId(request.getStrategyId())
                .build());
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
            @RequestBody StartStrategyRequest request) {

        if (strategyRunner.findById(strategyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (strategyRunner.isRunning(strategyId)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "策略已在运行中"));
        }
        strategyRunner.start(strategyId, request.getSymbol());
        return ResponseEntity.ok(Map.of("success", true, "strategyId", strategyId, "symbol", request.getSymbol()));
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
            @RequestBody KlineBacktestRequest request) {

        Strategy strategy = strategyRunner.findById(strategyId).orElse(null);
        if (strategy == null) return ResponseEntity.notFound().build();
        if (strategyRunner.isRunning(strategyId)) return ResponseEntity.badRequest().build();
        if (request.getStartMs() == null || request.getEndMs() == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                backtestEngine.runKlineBacktest(strategy, request.getSymbol(), request.getInterval(),
                        request.getStartMs(), request.getEndMs(), request.getCapital(), toBacktestConfig(request)));
    }

    /**
     * 基于 kline_{symbol} 分表的快速回测（取最新 N 根，无需指定时间范围）。
     */
    @PostMapping("/backtest/{strategyId}/kline/latest")
    public ResponseEntity<BacktestReport> runLatestKlineBacktest(
            @PathVariable String strategyId,
            @RequestBody LatestKlineBacktestRequest request) {

        Strategy strategy = strategyRunner.findById(strategyId).orElse(null);
        if (strategy == null) return ResponseEntity.notFound().build();
        if (strategyRunner.isRunning(strategyId)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                backtestEngine.quickKlineBacktest(strategy, request.getSymbol(), request.getInterval(),
                        request.getLimit(), request.getCapital()));
    }

    @PostMapping("/backtest/{strategyId}")
    public ResponseEntity<BacktestReport> runBacktest(
            @PathVariable String strategyId,
            @RequestBody QuickBacktestRequest request) {

        Strategy strategy = strategyRunner.findById(strategyId).orElse(null);
        if (strategy == null) {
            return ResponseEntity.notFound().build();
        }
        if (strategyRunner.isRunning(strategyId)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(backtestEngine.quickBacktest(strategy, request.getSymbol(),
                request.getDataCount(), request.getStartPrice(), request.getCapital()));
    }

    @GetMapping("/backtest")
    public List<BacktestReport> listBacktestReports(
            @RequestParam(required = false) String strategyId,
            @RequestParam(defaultValue = "20") int limit) {
        return backtestEngine.listReports(strategyId, limit);
    }

    private BacktestConfig toBacktestConfig(KlineBacktestRequest request) {
        PositionSizingMode sizingMode = request.getSizingMode() == null || request.getSizingMode().isBlank()
                ? PositionSizingMode.FIXED_QTY
                : PositionSizingMode.valueOf(request.getSizingMode().trim().toUpperCase());
        return BacktestConfig.builder()
                .sizingMode(sizingMode)
                .orderQuantity(request.getOrderQuantity())
                .orderNotional(request.getOrderNotional())
                .equityPercent(request.getEquityPercent())
                .allowPartialData(Boolean.TRUE.equals(request.getAllowPartialData()))
                .timezone(request.getTimezone())
                .build();
    }
}
