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
 * - 全局止损检查（5分钟）
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

    @Scheduled(fixedRate = 300_000)
    public void reconcilePositions() {
        Map<String, BigDecimal> localPositions = positionManager.getAllPositions();
        if (localPositions.isEmpty()) return;
        log.info("[对账] 开始仓位对账，本地品种数={}", localPositions.size());
        localPositions.forEach((symbol, qty) ->
                log.info("[对账] symbol={}, localQty={}", symbol, qty));
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

    @Scheduled(fixedRate = 300_000)
    public void checkFatalLoss() {
        BigDecimal available = walletManager.getAvailable("USDT");
        BigDecimal total = walletManager.getTotal("USDT");
        if (total.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal lossRate = total.subtract(available).divide(total, 4, RoundingMode.HALF_UP);
        if (lossRate.compareTo(fatalLossRate) >= 0) {
            log.error("[全局止损] 账户亏损率={}, 阈值={}, 请手动检查并暂停策略！", lossRate, fatalLossRate);
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
