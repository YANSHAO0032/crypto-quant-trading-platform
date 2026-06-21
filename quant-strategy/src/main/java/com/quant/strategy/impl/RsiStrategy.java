package com.quant.strategy.impl;

import com.quant.common.enums.Signal;
import com.quant.common.model.TickData;
import com.quant.strategy.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;

/**
 * RSI 策略 - 使用 Wilder 平滑移动平均（EMA变体）计算标准RSI。
 *
 * Wilder平滑公式：
 *   avgGain(t) = (avgGain(t-1) * (n-1) + gain(t)) / n
 *   avgLoss(t) = (avgLoss(t-1) * (n-1) + loss(t)) / n
 *
 * 首次需要 n+1 个数据点计算初始 SMA，之后用 Wilder 平滑递推。
 */
@Slf4j
@Component
public class RsiStrategy implements Strategy {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Value("${strategy.rsi.period:14}")
    private int rsiPeriod;

    @Value("${strategy.rsi.oversold:30}")
    private BigDecimal oversold;

    @Value("${strategy.rsi.overbought:70}")
    private BigDecimal overbought;

    private final LinkedList<BigDecimal> priceHistory = new LinkedList<>();

    /** Wilder 平滑后的平均涨幅/跌幅（初始化后维护） */
    private BigDecimal avgGain = null;
    private BigDecimal avgLoss = null;
    private boolean initialized = false;
    private boolean running = false;

    @Override
    public String getStrategyId() { return "RSI"; }

    @Override
    public String getStrategyName() { return "RSI策略"; }

    @Override
    public void init() {
        priceHistory.clear();
        avgGain = null;
        avgLoss = null;
        initialized = false;
        running = true;
        log.info("策略初始化: {}, period={}, oversold={}, overbought={}", getStrategyName(), rsiPeriod, oversold, overbought);
    }

    @Override
    public Signal onTick(TickData tick) {
        if (!running) return Signal.HOLD;

        priceHistory.addLast(tick.getLastPrice());

        // 阶段1：收集 rsiPeriod+1 个数据点，计算初始 SMA
        if (!initialized) {
            if (priceHistory.size() < rsiPeriod + 1) {
                return Signal.HOLD;
            }
            initWilder();
            initialized = true;
        } else {
            // 阶段2：Wilder 递推
            BigDecimal prev = priceHistory.get(priceHistory.size() - 2);
            BigDecimal curr = priceHistory.getLast();
            updateWilder(prev, curr);
            // 只保留最近两个价格用于下一次递推
            while (priceHistory.size() > 2) priceHistory.removeFirst();
        }

        BigDecimal rsi = calcRsi();
        if (rsi.compareTo(oversold) < 0) {
            log.info("[{}] RSI超卖信号: RSI={}, price={}", getStrategyId(), rsi, tick.getLastPrice());
            return Signal.BUY;
        } else if (rsi.compareTo(overbought) > 0) {
            log.info("[{}] RSI超买信号: RSI={}, price={}", getStrategyId(), rsi, tick.getLastPrice());
            return Signal.SELL;
        }
        return Signal.HOLD;
    }

    @Override
    public void stop() {
        running = false;
        log.info("策略停止: {}", getStrategyName());
    }

    @Override
    public boolean isRunning() { return running; }

    /** 用最初 rsiPeriod 个差值计算初始 SMA 作为 Wilder 平滑起点。 */
    private void initWilder() {
        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;
        for (int i = 1; i <= rsiPeriod; i++) {
            BigDecimal change = priceHistory.get(i).subtract(priceHistory.get(i - 1));
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                sumGain = sumGain.add(change);
            } else {
                sumLoss = sumLoss.add(change.abs());
            }
        }
        BigDecimal n = BigDecimal.valueOf(rsiPeriod);
        avgGain = sumGain.divide(n, 8, RoundingMode.HALF_UP);
        avgLoss = sumLoss.divide(n, 8, RoundingMode.HALF_UP);
    }

    /** Wilder 平滑递推：avgGain = (avgGain*(n-1) + gain) / n */
    private void updateWilder(BigDecimal prev, BigDecimal curr) {
        BigDecimal change = curr.subtract(prev);
        BigDecimal gain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
        BigDecimal loss = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;
        BigDecimal n = BigDecimal.valueOf(rsiPeriod);
        avgGain = avgGain.multiply(n.subtract(BigDecimal.ONE)).add(gain).divide(n, 8, RoundingMode.HALF_UP);
        avgLoss = avgLoss.multiply(n.subtract(BigDecimal.ONE)).add(loss).divide(n, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal calcRsi() {
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return HUNDRED;
        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
    }
}
