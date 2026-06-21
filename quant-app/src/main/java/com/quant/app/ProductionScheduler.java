package com.quant.app;

import com.quant.market.TickReceivedEvent;
import com.quant.risk.PositionManager;
import com.quant.risk.WalletManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生产定时任务：
 * - 仓位对账（本地 vs 交易所，5分钟）
 * - 行情心跳超时检测（1分钟）
 * - 全局止损检查（5分钟）：对比初始资金与当前总资金
 * - 钱包快照日志（1小时）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionScheduler {

    private final PositionManager positionManager;
    private final WalletManager walletManager;

    private final Map<String, Long> lastTickTimeMs = new ConcurrentHashMap<>();

    @Value("${risk.fatal-loss-rate:0.2}")
    private BigDecimal fatalLossRate;

    @Value("${risk.kline-timeout-seconds:120}")
    private int klineTimeoutSeconds;

    @Value("${wallet.default-usdt:10000}")
    private BigDecimal initialCapital;

    @Scheduled(fixedRate = 300_000)
    public void reconcilePositions() {
        Map<String, BigDecimal> localPositions = positionManager.getAllPositions();
        if (localPositions.isEmpty()) return;
        log.info("[对账] 开始仓位对账，本地品种数={}", localPositions.size());
        localPositions.forEach((symbol, qty) ->
                log.info("[对账] symbol={}, localQty={}", symbol, qty));
        // TODO: 调用 exchangeClient.queryPosition(symbol) 与本地对比，发现偏差则告警
    }

    @Scheduled(fixedRate = 60_000)
    public void checkKlineDelay() {
        long now = System.currentTimeMillis();
        lastTickTimeMs.forEach((symbol, lastMs) -> {
            long delaySeconds = (now - lastMs) / 1000;
            if (delaySeconds > klineTimeoutSeconds) {
                log.warn("[行情超时] symbol={}, 已超时 {}s，请检查 WebSocket 连接", symbol, delaySeconds);
            }
        });
    }

    /**
     * 全局止损检查：对比初始资金与当前 USDT 总资产，亏损超阈值则告警。
     * 修复原逻辑：原来计算的是冻结资金占比，而非真实亏损率。
     */
    @Scheduled(fixedRate = 300_000)
    public void checkFatalLoss() {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal currentTotal = walletManager.getTotal("USDT");
        BigDecimal lossRate = initialCapital.subtract(currentTotal)
                .divide(initialCapital, 4, RoundingMode.HALF_UP);
        if (lossRate.compareTo(fatalLossRate) >= 0) {
            log.error("[全局止损] 账户亏损率={}，阈值={}，初始资金={}，当前总资产={}，请手动检查并暂停策略！",
                    lossRate, fatalLossRate, initialCapital, currentTotal);
        }
    }

    @Scheduled(fixedRate = 3_600_000)
    public void logWalletSnapshot() {
        walletManager.snapshot().forEach((asset, vals) ->
                log.info("[钱包快照] {} available={} frozen={}", asset, vals[0], vals[1]));
    }

    @EventListener
    public void onTickReceived(TickReceivedEvent event) {
        lastTickTimeMs.put(event.getTick().getSymbol(), System.currentTimeMillis());
    }
}
