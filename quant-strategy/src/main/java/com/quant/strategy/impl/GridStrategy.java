package com.quant.strategy.impl;

import com.quant.common.enums.Signal;
import com.quant.common.model.TickData;
import com.quant.strategy.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 网格交易策略
 * 在设定的价格区间内，按等间距划分网格进行买卖
 */
@Slf4j
@Component
public class GridStrategy implements Strategy {

    @Value("${strategy.grid.upper-price:70000}")
    private BigDecimal upperPrice;

    @Value("${strategy.grid.lower-price:60000}")
    private BigDecimal lowerPrice;

    @Value("${strategy.grid.grid-count:10}")
    private int gridCount;

    private final List<BigDecimal> gridLevels = new ArrayList<>();

    private boolean running = false;
    private BigDecimal lastPrice = BigDecimal.ZERO;

    @Override
    public String getStrategyId() {
        return "GRID";
    }

    @Override
    public String getStrategyName() {
        return "网格交易策略";
    }

    @Override
    public void init() {
        gridLevels.clear();
        lastPrice = BigDecimal.ZERO;
        BigDecimal step = upperPrice.subtract(lowerPrice)
                .divide(BigDecimal.valueOf(gridCount), 8, RoundingMode.HALF_UP);
        for (int i = 0; i <= gridCount; i++) {
            gridLevels.add(lowerPrice.add(step.multiply(BigDecimal.valueOf(i))));
        }
        running = true;
        log.info("网格策略初始化: upper={}, lower={}, gridCount={}, levels={}",
                upperPrice, lowerPrice, gridCount, gridLevels);
    }

    @Override
    public Signal onTick(TickData tick) {
        if (!running) return Signal.HOLD;

        BigDecimal currentPrice = tick.getLastPrice();
        if (lastPrice.compareTo(BigDecimal.ZERO) == 0) {
            lastPrice = currentPrice;
            return Signal.HOLD;
        }

        Signal signal = Signal.HOLD;

        for (BigDecimal level : gridLevels) {
            if (lastPrice.compareTo(level) >= 0 && currentPrice.compareTo(level) < 0) {
                signal = Signal.BUY;
                log.info("[{}] 触发买入网格: level={}, price={}", getStrategyId(), level, currentPrice);
                break;
            }
            if (lastPrice.compareTo(level) <= 0 && currentPrice.compareTo(level) > 0) {
                signal = Signal.SELL;
                log.info("[{}] 触发卖出网格: level={}, price={}", getStrategyId(), level, currentPrice);
                break;
            }
        }

        lastPrice = currentPrice;
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
}
