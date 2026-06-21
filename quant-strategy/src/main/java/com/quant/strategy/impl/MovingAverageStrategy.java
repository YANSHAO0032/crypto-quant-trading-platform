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
public class MovingAverageStrategy implements Strategy {

    @Value("${strategy.ma.short-period:5}")
    private int shortPeriod;

    @Value("${strategy.ma.long-period:20}")
    private int longPeriod;

    private final LinkedList<BigDecimal> priceHistory = new LinkedList<>();
    private boolean running = false;
    private BigDecimal prevShortMa = BigDecimal.ZERO;
    private BigDecimal prevLongMa = BigDecimal.ZERO;

    @Override
    public String getStrategyId() {
        return "MA_CROSS";
    }

    @Override
    public String getStrategyName() {
        return "双均线交叉策略";
    }

    @Override
    public void init() {
        priceHistory.clear();
        prevShortMa = BigDecimal.ZERO;
        prevLongMa = BigDecimal.ZERO;
        running = true;
        log.info("策略初始化: {}, shortPeriod={}, longPeriod={}", getStrategyName(), shortPeriod, longPeriod);
    }

    @Override
    public Signal onTick(TickData tick) {
        if (!running) return Signal.HOLD;

        priceHistory.addLast(tick.getLastPrice());
        if (priceHistory.size() > longPeriod) {
            priceHistory.removeFirst();
        }
        if (priceHistory.size() < longPeriod) {
            return Signal.HOLD;
        }

        BigDecimal shortMa = calcMA(shortPeriod);
        BigDecimal longMa = calcMA(longPeriod);

        Signal signal = Signal.HOLD;
        if (prevShortMa.compareTo(prevLongMa) <= 0 && shortMa.compareTo(longMa) > 0) {
            signal = Signal.BUY;
            log.info("[{}] 金叉信号: shortMA={}, longMA={}, price={}", getStrategyId(), shortMa, longMa, tick.getLastPrice());
        } else if (prevShortMa.compareTo(prevLongMa) >= 0 && shortMa.compareTo(longMa) < 0) {
            signal = Signal.SELL;
            log.info("[{}] 死叉信号: shortMA={}, longMA={}, price={}", getStrategyId(), shortMa, longMa, tick.getLastPrice());
        }

        prevShortMa = shortMa;
        prevLongMa = longMa;
        return signal;
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

    private BigDecimal calcMA(int period) {
        return priceHistory.stream()
                .skip(priceHistory.size() - period)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }
}
