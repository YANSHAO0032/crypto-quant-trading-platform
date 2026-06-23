package com.quant.strategy.impl;

import com.quant.common.enums.Signal;
import com.quant.common.model.TickData;
import com.quant.strategy.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;

/**
 * @author: 慕黎尘渊
 * @description: 均值回归
 * @date: 2026/6/23 21:36
 * @version: 1.0
 */
@Slf4j
@Component
public class ProRsiMeanReversionStrategy implements Strategy {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Value("${strategy.rsi.period:14}")
    private int rsiPeriod;

    @Value("${strategy.ma.period:20}")
    private int maPeriod;

    @Value("${strategy.rsi.oversold:30}")
    private BigDecimal oversold;

    @Value("${strategy.rsi.overbought:70}")
    private BigDecimal overbought;

    @Value("${strategy.zscore.entry:2.0}")
    private BigDecimal entryZ;

    private final LinkedList<BigDecimal> prices = new LinkedList<>();

    private BigDecimal avgGain;
    private BigDecimal avgLoss;
    private boolean initialized = false;
    private boolean running = false;

    // ====== 仓位状态（关键） ======
    private enum Position {
        FLAT, LONG, SHORT
    }

    private Position position = Position.FLAT;

    @Override
    public String getStrategyId() {
        return "PRO_RSI_MR";
    }

    @Override
    public String getStrategyName() {
        return "专业RSI均值回归策略";
    }

    @Override
    public void init() {
        prices.clear();
        avgGain = null;
        avgLoss = null;
        initialized = false;
        running = true;
        position = Position.FLAT;
        log.info("策略初始化完成: {}", getStrategyName());
    }

    @Override
    public Signal onTick(TickData tick) {

        if (!running) return Signal.HOLD;

        BigDecimal price = tick.getLastPrice();
        prices.addLast(price);

        if (prices.size() > maPeriod + 1) {
            prices.removeFirst();
        }

        // ========== 1. 初始化 RSI ==========
        if (!initialized) {
            if (prices.size() < rsiPeriod + 1) return Signal.HOLD;
            initWilder();
            initialized = true;
        } else {
            BigDecimal prev = prices.get(prices.size() - 2);
            updateWilder(prev, price);
        }

        // ========== 2. 计算 RSI ==========
        BigDecimal rsi = calcRsi();

        // ========== 3. 计算均值（MA） ==========
        BigDecimal ma = calcMA();

        // ========== 4. 偏离度 ==========
        BigDecimal z = price.subtract(ma)
                .divide(ma, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        // ========== 5. 日内均值回归逻辑 ==========
        Signal signal = Signal.HOLD;

        // ---- 开仓逻辑 ----
        if (position == Position.FLAT) {

            if (rsi.compareTo(oversold) < 0 && z.compareTo(entryZ.negate()) < 0) {
                signal = Signal.BUY;
                position = Position.LONG;
                log.info("[ENTRY-LONG] RSI={}, Z={}, price={}", rsi, z, price);
            }

            else if (rsi.compareTo(overbought) > 0 && z.compareTo(entryZ) > 0) {
                signal = Signal.SELL;
                position = Position.SHORT;
                log.info("[ENTRY-SHORT] RSI={}, Z={}, price={}", rsi, z, price);
            }
        }

        // ---- 均值回归退出逻辑（核心） ----
        else if (position == Position.LONG) {

            if (price.compareTo(ma) >= 0) {
                signal = Signal.SELL;
                position = Position.FLAT;
                log.info("[EXIT-LONG] price back to MA, RSI={}, Z={}", rsi, z);
            }
        }

        else if (position == Position.SHORT) {

            if (price.compareTo(ma) <= 0) {
                signal = Signal.BUY;
                position = Position.FLAT;
                log.info("[EXIT-SHORT] price back to MA, RSI={}, Z={}", rsi, z);
            }
        }

        return signal;
    }

    // ================= RSI =================

    private void initWilder() {
        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;

        for (int i = 1; i <= rsiPeriod; i++) {
            BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
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

    private void updateWilder(BigDecimal prev, BigDecimal curr) {
        BigDecimal change = curr.subtract(prev);

        BigDecimal gain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
        BigDecimal loss = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;

        BigDecimal n = BigDecimal.valueOf(rsiPeriod);

        avgGain = avgGain.multiply(n.subtract(BigDecimal.ONE))
                .add(gain)
                .divide(n, 8, RoundingMode.HALF_UP);

        avgLoss = avgLoss.multiply(n.subtract(BigDecimal.ONE))
                .add(loss)
                .divide(n, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal calcRsi() {
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return HUNDRED;

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);

        return HUNDRED.subtract(
                HUNDRED.divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP)
        );
    }

    // ================= MA =================

    private BigDecimal calcMA() {
        return prices.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 8, RoundingMode.HALF_UP);
    }

    @Override
    public void stop() {
        running = false;
        position = Position.FLAT;
        log.info("策略停止: {}", getStrategyName());
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
