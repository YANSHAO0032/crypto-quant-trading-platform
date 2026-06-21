package com.quant.backtest;

import com.quant.common.enums.Signal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 绩效分析器
 * 分析回测结果，计算各项绩效指标
 */
@Slf4j
@Component
public class PerformanceAnalyzer {

    /**
     * 分析回测结果
     * @param trades 交易信号与价格列表
     * @param initialCapital 初始资金
     * @return 绩效报告
     */
    public PerformanceReport analyze(List<TradeRecord> trades, BigDecimal initialCapital) {
        if (trades.isEmpty()) {
            return PerformanceReport.builder()
                    .initialCapital(initialCapital)
                    .finalCapital(initialCapital)
                    .totalReturn(BigDecimal.ZERO)
                    .totalTrades(0)
                    .winCount(0)
                    .lossCount(0)
                    .winRate(BigDecimal.ZERO)
                    .maxDrawdown(BigDecimal.ZERO)
                    .profitFactor(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal capital = initialCapital;
        BigDecimal maxCapital = initialCapital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        int winCount = 0;
        int lossCount = 0;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;

        BigDecimal entryPrice = BigDecimal.ZERO;
        boolean inPosition = false;

        for (TradeRecord trade : trades) {
            if (trade.getSignal() == Signal.BUY && !inPosition) {
                entryPrice = trade.getPrice();
                inPosition = true;
            } else if (trade.getSignal() == Signal.SELL && inPosition) {
                BigDecimal pnl = trade.getPrice().subtract(entryPrice);
                capital = capital.add(pnl);

                if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                    winCount++;
                    totalProfit = totalProfit.add(pnl);
                } else {
                    lossCount++;
                    totalLoss = totalLoss.add(pnl.abs());
                }

                if (capital.compareTo(maxCapital) > 0) {
                    maxCapital = capital;
                }
                BigDecimal drawdown = maxCapital.subtract(capital)
                        .divide(maxCapital, 8, RoundingMode.HALF_UP);
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }

                inPosition = false;
            }
        }

        int totalTrades = winCount + lossCount;
        BigDecimal winRate = totalTrades > 0
                ? BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal profitFactor = totalLoss.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.divide(totalLoss, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalReturn = capital.subtract(initialCapital)
                .divide(initialCapital, 4, RoundingMode.HALF_UP);

        PerformanceReport report = PerformanceReport.builder()
                .initialCapital(initialCapital)
                .finalCapital(capital)
                .totalReturn(totalReturn)
                .totalTrades(totalTrades)
                .winCount(winCount)
                .lossCount(lossCount)
                .winRate(winRate)
                .maxDrawdown(maxDrawdown)
                .profitFactor(profitFactor)
                .build();

        log.info("===== 回测绩效报告 =====");
        log.info("初始资金: {}", initialCapital);
        log.info("最终资金: {}", capital);
        log.info("总收益率: {}%", totalReturn.multiply(BigDecimal.valueOf(100)));
        log.info("总交易次数: {}", totalTrades);
        log.info("胜率: {}%", winRate.multiply(BigDecimal.valueOf(100)));
        log.info("最大回撤: {}%", maxDrawdown.multiply(BigDecimal.valueOf(100)));
        log.info("盈亏比: {}", profitFactor);
        log.info("========================");

        return report;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeRecord {
        private String backtestId;
        private int sequenceNo;
        private Signal signal;
        private BigDecimal price;
        private long timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceReport {
        private String backtestId;
        private String strategyId;
        private String strategyName;
        private String symbol;
        private int dataCount;
        private BigDecimal initialCapital;
        private BigDecimal finalCapital;
        private BigDecimal totalReturn;
        private int totalTrades;
        private int winCount;
        private int lossCount;
        private BigDecimal winRate;
        private BigDecimal maxDrawdown;
        private BigDecimal profitFactor;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;
    }
}
