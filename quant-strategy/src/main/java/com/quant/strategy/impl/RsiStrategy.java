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
    private boolean running = false;

    @Override
    public String getStrategyId() {
        return "RSI";
    }

    @Override
    public String getStrategyName() {
        return "RSI策略";
    }

    @Override
    public void init() {
        priceHistory.clear();
        running = true;
        log.info("策略初始化: {}, period={}, oversold={}, overbought={}", getStrategyName(), rsiPeriod, oversold, overbought);
    }

    @Override
    public Signal onTick(TickData tick) {
        if (!running) return Signal.HOLD;

        priceHistory.addLast(tick.getLastPrice());
        if (priceHistory.size() > rsiPeriod + 1) {
            priceHistory.removeFirst();
        }
        if (priceHistory.size() <= rsiPeriod) {
            return Signal.HOLD;
        }

        BigDecimal rsi = calculateRSI();
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
    public boolean isRunning() {
        return running;
    }

    private BigDecimal calculateRSI() {
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;

        for (int i = 1; i < priceHistory.size(); i++) {
            BigDecimal change = priceHistory.get(i).subtract(priceHistory.get(i - 1));
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }

        BigDecimal n = BigDecimal.valueOf(rsiPeriod);
        avgGain = avgGain.divide(n, 8, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(n, 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return HUNDRED;
        }

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
    }
}
