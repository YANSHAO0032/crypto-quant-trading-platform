package com.quant.strategy;

import com.quant.common.enums.Signal;
import com.quant.common.model.TickData;
import com.quant.market.MarketDataService;
import com.quant.market.websocket.BinanceWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略运行管理器。
 *
 * 负责策略完整生命周期：
 *   start(strategyId, symbol) → init() + WS connect + marketDataService.subscribe()
 *   stop(strategyId)          → strategy.stop() + marketDataService.unsubscribe()
 *
 * 信号通过 SignalEvent 事件发布，由 quant-oms 的 SignalOrderHandler 监听并下单，
 * 实现策略模块与OMS模块解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyRunner {

    private final List<Strategy> strategies;
    private final MarketDataService marketDataService;
    private final BinanceWebSocketClient webSocketClient;
    private final ApplicationEventPublisher eventPublisher;

    /** 每笔订单的名义数量（可配置，真实项目中应按资金和价格动态计算） */
    @Value("${strategy.order-quantity:0.001}")
    private BigDecimal orderQuantity;

    /** strategyId → 当前订阅的 symbol */
    private final Map<String, String> runningMap = new ConcurrentHashMap<>();

    public List<Strategy> getAll() {
        return strategies;
    }

    public Optional<Strategy> findById(String strategyId) {
        return strategies.stream()
                .filter(s -> s.getStrategyId().equals(strategyId))
                .findFirst();
    }

    /**
     * 启动策略：初始化状态 + 建立WebSocket行情连接 + 订阅行情（含 onCheckExit 联动）。
     *
     * @return false 表示策略不存在或已在运行
     */
    public boolean start(String strategyId, String symbol) {
        Optional<Strategy> opt = findById(strategyId);
        if (opt.isEmpty()) {
            log.warn("策略不存在: {}", strategyId);
            return false;
        }
        Strategy strategy = opt.get();
        if (runningMap.containsKey(strategyId)) {
            log.warn("策略已在运行: strategyId={}, symbol={}", strategyId, runningMap.get(strategyId));
            return false;
        }

        strategy.init();

        // 建立 WebSocket 行情连接（幂等：WebSocketClient内部按symbol管理连接）
        webSocketClient.connect(symbol);

        // 注册行情回调：onTick → 信号 → 发布 SignalEvent → OMS下单
        marketDataService.subscribe(symbol, tick -> handleTick(strategy, strategyId, symbol, tick));

        runningMap.put(strategyId, symbol);
        log.info("策略已启动: strategyId={}, symbol={}", strategyId, symbol);
        return true;
    }

    /**
     * 停止策略：停止状态 + 取消行情订阅。
     *
     * @return false 表示策略不存在或未在运行
     */
    public boolean stop(String strategyId) {
        Optional<Strategy> opt = findById(strategyId);
        if (opt.isEmpty()) {
            log.warn("策略不存在: {}", strategyId);
            return false;
        }
        String symbol = runningMap.remove(strategyId);
        if (symbol == null) {
            log.warn("策略未在运行: {}", strategyId);
            return false;
        }

        opt.get().stop();

        // 仅当该symbol没有其他策略在跑时，才取消订阅
        boolean symbolStillNeeded = runningMap.values().stream().anyMatch(s -> s.equals(symbol));
        if (!symbolStillNeeded) {
            marketDataService.unsubscribe(symbol);
        }

        log.info("策略已停止: strategyId={}, symbol={}", strategyId, symbol);
        return true;
    }

    public boolean isRunning(String strategyId) {
        return runningMap.containsKey(strategyId);
    }

    /** 当前运行的策略快照：strategyId → symbol */
    public Map<String, String> runningSnapshot() {
        return Map.copyOf(runningMap);
    }

    private void handleTick(Strategy strategy, String strategyId, String symbol, TickData tick) {
        Signal signal = strategy.onTick(tick);
        if (signal == Signal.HOLD) {
            signal = strategy.onCheckExit(tick);
        }
        if (signal != Signal.HOLD) {
            log.info("策略信号: strategyId={}, symbol={}, signal={}, price={}",
                    strategyId, symbol, signal, tick.getLastPrice());
            // 发布信号事件，由 SignalOrderHandler（quant-oms）监听并下单，保持模块解耦
            eventPublisher.publishEvent(
                    new SignalEvent(this, strategyId, symbol, signal, tick.getLastPrice(), orderQuantity));
        }
    }
}
