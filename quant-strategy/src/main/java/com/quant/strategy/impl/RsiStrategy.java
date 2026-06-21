package com.quant.strategy.impl;

import com.quant.common.enums.Signal;
import com.quant.common.model.TickData;
import com.quant.strategy.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;

/**
 * RSI策略
 * RSI < 30 超卖 -> BUY
 * RSI > 70 超买 -> SELL
 */
@Slf4j
@Component
public class RsiStrategy implements Strategy {

    private static final int RSI_PERIOD = 14;
    private static final BigDecimal OVERSOLD = new BigDecimal("30");
    private static final BigDecimal OVERBOUGHT = new BigDecimal("70");

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
        log.info("策略初始化: {}", getStrategyName());
    }

    @Override
    public Signal onTick(TickData tick) {
        if (!running) return Signal.HOLD;

        priceHistory.addLast(tick.getLastPrice());
        if (priceHistory.size() > RSI_PERIOD + 1) {
            priceHistory.removeFirst();
        }

        if (priceHistory.size() <= RSI_PERIOD) {
            return Signal.HOLD;
        }

        BigDecimal rsi = calculateRSI();

        if (rsi.compareTo(OVERSOLD) < 0) {
            log.info("[{}] RSI超卖信号: RSI={}, price={}", getStrategyId(), rsi, tick.getLastPrice());
            return Signal.BUY;
        } else if (rsi.compareTo(OVERBOUGHT) > 0) {
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

        avgGain = avgGain.divide(BigDecimal.valueOf(RSI_PERIOD), 8, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(RSI_PERIOD), 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("100");
        }

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        return new BigDecimal("100").subtract(
                new BigDecimal("100").divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP)
        );
    }
}
