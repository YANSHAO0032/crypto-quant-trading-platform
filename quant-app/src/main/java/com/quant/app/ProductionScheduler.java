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
 * 生产定时任务：对齐 banbot startJobs 体系，提供运维级监控能力。
 *
 * - 仓位对账（本地 vs 交易所）
 * - 行情心跳超时检测
 * - 全局止损检查
 * - 钱包快照日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionScheduler {

    private final PositionManager positionManager;
    private final WalletManager walletManager;

    /** 每个订阅 symbol 的最后行情时间（由行情事件更新） */
    private final Map<String, Long> lastTickTimeMs = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${risk.fatal-loss-rate:0.2}")
    private BigDecimal fatalLossRate;

    @Value("${risk.kline-timeout-seconds:120}")
    private int klineTimeoutSeconds;

    // -------- 仓位对账（每5分钟） --------
    // 对比本地 quant_position 与交易所实际持仓，发现不一致时告警
    @Scheduled(fixedRate = 300_000)
    public void reconcilePositions() {
        Map<String, BigDecimal> localPositions = positionManager.getAllPositions();
        if (localPositions.isEmpty()) return;

        log.info("[对账] 开始仓位对账，本地品种数={}", localPositions.size());
        localPositions.forEach((symbol, qty) ->
                log.info("[对账] symbol={}, localQty={}", symbol, qty));
        // 实际对接交易所查询后做差值校验，当前记录本地快照待扩展
    }

    // -------- 行情心跳超时检测（每1分钟）--------
    // 超过 klineTimeoutSeconds 无行情则告警（WebSocket 断线可感知）
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

    // -------- 全局止损检查（每5分钟）--------
    // 若账户亏损超过 fatalLossRate 则告警停机保护
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

    // -------- 钱包快照日志（每小时）--------
    @Scheduled(fixedRate = 3_600_000)
    public void logWalletSnapshot() {
        Map<String, BigDecimal[]> snap = walletManager.snapshot();
        snap.forEach((asset, vals) ->
                log.info("[钱包快照] {} available={} frozen={}", asset, vals[0], vals[1]));
    }

    /**
     * 由行情 TickReceivedEvent 更新 symbol 心跳时间（解耦，无循环依赖）。
     */
    @EventListener
    public void onTickReceived(TickReceivedEvent event) {
        lastTickTimeMs.put(event.getTick().getSymbol(), System.currentTimeMillis());
    }

    /** 由行情层调用，更新 symbol 心跳时间。
     * @deprecated 请改用 TickReceivedEvent 事件监听
     */
    @Deprecated
    public void onTickReceived(String symbol) {
        lastTickTimeMs.put(symbol.toUpperCase(), System.currentTimeMillis());
    }
}
