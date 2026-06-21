package com.quant.strategy;

import com.quant.common.enums.Signal;
import com.quant.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略运行管理器。
 *
 * 负责策略完整生命周期：
 *   start(strategyId, symbol) → init() + marketDataService.subscribe()
 *   stop(strategyId)          → strategy.stop() + marketDataService.unsubscribe()
 *
 * 维护 strategyId → symbol 的运行映射，作为"哪些策略当前在跑哪个交易对"的唯一状态源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyRunner {

    private final List<Strategy> strategies;
    private final MarketDataService marketDataService;

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
     * 启动策略：初始化状态 + 订阅行情（含 onCheckExit 联动）。
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
        marketDataService.subscribe(symbol, tick -> {
            Signal signal = strategy.onTick(tick);
            if (signal == Signal.HOLD) {
                signal = strategy.onCheckExit(tick);
            }
            if (signal != Signal.HOLD) {
                log.info("策略信号: strategyId={}, symbol={}, signal={}, price={}",
                        strategyId, symbol, signal, tick.getLastPrice());
            }
        });
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
        marketDataService.unsubscribe(symbol);
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
}
