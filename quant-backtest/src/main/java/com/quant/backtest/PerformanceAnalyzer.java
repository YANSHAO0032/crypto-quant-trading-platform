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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效分析器：计算 Sharpe/Sortino/年化收益/按日分组统计
 */
@Slf4j
@Component
public class PerformanceAnalyzer {

    private static final BigDecimal TRADING_DAYS = new BigDecimal("365");
    private static final int SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    public PerformanceReport analyze(List<TradeRecord> trades, BigDecimal initialCapital) {
        if (trades.isEmpty()) {
            return emptyReport(initialCapital);
        }

        Map<LocalDate, BigDecimal> dailyPnl = new LinkedHashMap<>();
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
                BigDecimal drawdown = maxCapital.subtract(capital).divide(maxCapital, SCALE, RM);
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }

                LocalDate date = Instant.ofEpochMilli(trade.getTimestamp())
                        .atZone(ZoneOffset.UTC).toLocalDate();
                dailyPnl.merge(date, pnl, BigDecimal::add);
                inPosition = false;
            }
        }

        // 日收益率序列（用于 Sharpe/Sortino）
        List<BigDecimal> dailyReturns = new ArrayList<>();
        BigDecimal runCapital = initialCapital;
        for (BigDecimal pnl : dailyPnl.values()) {
            BigDecimal ret = runCapital.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : pnl.divide(runCapital, SCALE, RM);
            dailyReturns.add(ret);
            runCapital = runCapital.add(pnl);
        }

        int totalTrades = winCount + lossCount;
        BigDecimal winRate = totalTrades > 0
                ? BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(totalTrades), 4, RM)
                : BigDecimal.ZERO;
        BigDecimal profitFactor = totalLoss.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.divide(totalLoss, 4, RM)
                : BigDecimal.ZERO;
        BigDecimal totalReturn = capital.subtract(initialCapital).divide(initialCapital, 4, RM);
        int days = dailyPnl.size();
        BigDecimal annualizedReturn = days > 0
                ? totalReturn.multiply(TRADING_DAYS).divide(BigDecimal.valueOf(days), 4, RM)
                : BigDecimal.ZERO;

        PerformanceReport report = PerformanceReport.builder()
                .initialCapital(initialCapital)
                .finalCapital(capital)
                .totalReturn(totalReturn)
                .annualizedReturn(annualizedReturn)
                .totalTrades(totalTrades)
                .winCount(winCount)
                .lossCount(lossCount)
                .winRate(winRate)
                .maxDrawdown(maxDrawdown)
                .profitFactor(profitFactor)
                .sharpeRatio(calcSharpe(dailyReturns))
                .sortinoRatio(calcSortino(dailyReturns))
                .dailyStats(buildDailyStats(dailyPnl, initialCapital))
                .build();

        logReport(report);
        return report;
    }

    /** 年化 Sharpe（无风险利率=0） */
    private BigDecimal calcSharpe(List<BigDecimal> returns) {
        if (returns.size() < 2) return BigDecimal.ZERO;
        BigDecimal mean = mean(returns);
        BigDecimal std = std(returns, mean);
        if (std.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        double sharpe = mean.divide(std, SCALE, RM).doubleValue() * Math.sqrt(365);
        return BigDecimal.valueOf(sharpe).setScale(4, RM);
    }

    /** 年化 Sortino（只惩罚下行波动） */
    private BigDecimal calcSortino(List<BigDecimal> returns) {
        if (returns.size() < 2) return BigDecimal.ZERO;
        BigDecimal mean = mean(returns);
        List<BigDecimal> downside = returns.stream()
                .filter(r -> r.compareTo(BigDecimal.ZERO) < 0)
                .toList();
        if (downside.isEmpty()) return new BigDecimal("999.0000");
        BigDecimal downStd = std(downside, BigDecimal.ZERO);
        if (downStd.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        double sortino = mean.divide(downStd, SCALE, RM).doubleValue() * Math.sqrt(365);
        return BigDecimal.valueOf(sortino).setScale(4, RM);
    }

    private BigDecimal mean(List<BigDecimal> list) {
        return list.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(list.size()), SCALE, RM);
    }

    private BigDecimal std(List<BigDecimal> list, BigDecimal mean) {
        BigDecimal sumSq = list.stream()
                .map(r -> r.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return BigDecimal.valueOf(
                Math.sqrt(sumSq.divide(BigDecimal.valueOf(list.size()), SCALE, RM).doubleValue()))
                .setScale(SCALE, RM);
    }

    private List<DailyStats> buildDailyStats(Map<LocalDate, BigDecimal> dailyPnl, BigDecimal initCapital) {
        List<DailyStats> result = new ArrayList<>();
        BigDecimal running = initCapital;
        for (Map.Entry<LocalDate, BigDecimal> e : dailyPnl.entrySet()) {
            BigDecimal ret = running.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : e.getValue().divide(running, 4, RM);
            result.add(DailyStats.builder().date(e.getKey()).pnl(e.getValue()).returnRate(ret).build());
            running = running.add(e.getValue());
        }
        return result;
    }

    private PerformanceReport emptyReport(BigDecimal initialCapital) {
        return PerformanceReport.builder()
                .initialCapital(initialCapital).finalCapital(initialCapital)
                .totalReturn(BigDecimal.ZERO).annualizedReturn(BigDecimal.ZERO)
                .totalTrades(0).winCount(0).lossCount(0)
                .winRate(BigDecimal.ZERO).maxDrawdown(BigDecimal.ZERO).profitFactor(BigDecimal.ZERO)
                .sharpeRatio(BigDecimal.ZERO).sortinoRatio(BigDecimal.ZERO).dailyStats(List.of())
                .build();
    }

    private void logReport(PerformanceReport r) {
        log.info("===== 回测绩效报告 =====");
        log.info("初始资金: {}  最终资金: {}", r.getInitialCapital(), r.getFinalCapital());
        log.info("总收益率: {}%  年化收益率: {}%",
                r.getTotalReturn().multiply(BigDecimal.valueOf(100)),
                r.getAnnualizedReturn().multiply(BigDecimal.valueOf(100)));
        log.info("总交易次数: {}  胜率: {}%", r.getTotalTrades(),
                r.getWinRate().multiply(BigDecimal.valueOf(100)));
        log.info("最大回撤: {}%  盈亏比: {}",
                r.getMaxDrawdown().multiply(BigDecimal.valueOf(100)), r.getProfitFactor());
        log.info("Sharpe Ratio: {}  Sortino Ratio: {}", r.getSharpeRatio(), r.getSortinoRatio());
        log.info("========================");
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TradeRecord {
        private String backtestId;
        private int sequenceNo;
        private Signal signal;
        private BigDecimal price;
        private long timestamp;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyStats {
        private LocalDate date;
        private BigDecimal pnl;
        private BigDecimal returnRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PerformanceReport {
        private String backtestId;
        private String strategyId;
        private String strategyName;
        private String symbol;
        private int dataCount;
        private BigDecimal initialCapital;
        private BigDecimal finalCapital;
        private BigDecimal totalReturn;
        private BigDecimal annualizedReturn;
        private int totalTrades;
        private int winCount;
        private int lossCount;
        private BigDecimal winRate;
        private BigDecimal maxDrawdown;
        private BigDecimal profitFactor;
        private BigDecimal sharpeRatio;
        private BigDecimal sortinoRatio;
        private List<DailyStats> dailyStats;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;
    }
}
