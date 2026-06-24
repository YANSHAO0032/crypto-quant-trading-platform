package com.quant.backtest;

import com.quant.common.enums.Signal;
import com.quant.common.model.BacktestDailyStats;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.BacktestTradeRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绩效分析器：计算 Sharpe/Sortino/年化收益/按日分组统计。
 * 直接使用 quant-common 中的模型，不在本类内部重复定义数据结构。
 */
@Slf4j
@Component
public class PerformanceAnalyzer {

    private static final BigDecimal TRADING_DAYS = new BigDecimal("365");
    private static final int SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    public BacktestReport analyze(BacktestRunResult result) {
        return analyze(result, DEFAULT_TIMEZONE);
    }

    public BacktestReport analyze(BacktestRunResult result, String timezone) {
        if (result.getEquityCurve() == null || result.getEquityCurve().isEmpty()) {
            return emptyReport(result.getInitialCapital());
        }

        ZoneId zoneId = ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone);
        List<EquityPoint> curve = result.getEquityCurve().stream()
                .sorted(Comparator.comparing(EquityPoint::getTimestampMs))
                .toList();
        BigDecimal initialCapital = scale(result.getInitialCapital());
        BigDecimal finalCapital = scale(curve.get(curve.size() - 1).getEquity());
        BigDecimal totalReturn = finalCapital.subtract(initialCapital).divide(initialCapital, 4, RM);
        BigDecimal maxDrawdown = calcMaxDrawdown(curve);

        List<BacktestClosedTrade> closedTrades = result.getClosedTrades() == null ? List.of() : result.getClosedTrades();
        int winCount = 0;
        int lossCount = 0;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;
        for (BacktestClosedTrade trade : closedTrades) {
            BigDecimal pnl = trade.getNetPnl();
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winCount++;
                totalProfit = totalProfit.add(pnl);
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                lossCount++;
                totalLoss = totalLoss.add(pnl.abs());
            }
        }

        List<BacktestDailyStats> dailyStats = buildDailyStatsFromEquity(curve, initialCapital, zoneId);
        List<BigDecimal> dailyReturns = buildDailyReturnsFromEquity(curve, initialCapital, zoneId);

        int totalTrades = closedTrades.size();
        BigDecimal winRate = totalTrades > 0
                ? BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(totalTrades), 4, RM)
                : BigDecimal.ZERO;
        BigDecimal profitFactor = totalLoss.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.divide(totalLoss, 4, RM)
                : BigDecimal.ZERO;
        int days = dailyStats.size();
        BigDecimal annualizedReturn = days > 0
                ? totalReturn.multiply(TRADING_DAYS).divide(BigDecimal.valueOf(days), 4, RM)
                : BigDecimal.ZERO;

        BacktestReport report = BacktestReport.builder()
                .initialCapital(initialCapital)
                .finalCapital(finalCapital)
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
                .dailyStats(dailyStats)
                .build();

        logReport(report);
        return report;
    }

    private BigDecimal calcSharpe(List<BigDecimal> returns) {
        if (returns.size() < 2) return BigDecimal.ZERO;
        double mean = meanAsDouble(returns);
        double std = stdAsDouble(returns, mean);
        if (std == 0D) return BigDecimal.ZERO;
        double sharpe = mean / std * Math.sqrt(365);
        return BigDecimal.valueOf(sharpe).setScale(4, RM);
    }

    private BigDecimal calcSortino(List<BigDecimal> returns) {
        if (returns.size() < 2) return BigDecimal.ZERO;
        double mean = meanAsDouble(returns);
        List<BigDecimal> downside = returns.stream()
                .filter(r -> r.compareTo(BigDecimal.ZERO) < 0)
                .toList();
        if (downside.isEmpty()) return new BigDecimal("999.0000");
        double downStd = stdAsDouble(downside, 0D);
        if (downStd == 0D) return BigDecimal.ZERO;
        double sortino = mean / downStd * Math.sqrt(365);
        return BigDecimal.valueOf(sortino).setScale(4, RM);
    }

    private double meanAsDouble(List<BigDecimal> list) {
        return list.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0D);
    }

    private double stdAsDouble(List<BigDecimal> list, double mean) {
        double variance = list.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .map(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0D);
        return Math.sqrt(variance);
    }


    private BigDecimal calcMaxDrawdown(List<EquityPoint> curve) {
        BigDecimal peak = curve.get(0).getEquity();
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (EquityPoint point : curve) {
            if (point.getEquity().compareTo(peak) > 0) {
                peak = point.getEquity();
            }
            if (peak.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal drawdown = peak.subtract(point.getEquity()).divide(peak, SCALE, RM);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    private List<BacktestDailyStats> buildDailyStatsFromEquity(List<EquityPoint> curve, BigDecimal initCapital,
                                                               ZoneId zoneId) {
        Map<LocalDate, BigDecimal> dailyCloseEquity = new LinkedHashMap<>();
        for (EquityPoint point : curve) {
            LocalDate date = Instant.ofEpochMilli(point.getTimestampMs())
                    .atZone(zoneId).toLocalDate();
            dailyCloseEquity.put(date, point.getEquity());
        }

        List<BacktestDailyStats> result = new ArrayList<>();
        BigDecimal previousEquity = initCapital;
        for (Map.Entry<LocalDate, BigDecimal> entry : dailyCloseEquity.entrySet()) {
            BigDecimal pnl = entry.getValue().subtract(previousEquity);
            BigDecimal returnRate = previousEquity.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : pnl.divide(previousEquity, 4, RM);
            result.add(BacktestDailyStats.builder()
                    .date(entry.getKey())
                    .pnl(pnl)
                    .returnRate(returnRate)
                    .build());
            previousEquity = entry.getValue();
        }
        return result;
    }

    private List<BigDecimal> buildDailyReturnsFromEquity(List<EquityPoint> curve, BigDecimal initCapital,
                                                         ZoneId zoneId) {
        Map<LocalDate, BigDecimal> dailyCloseEquity = new LinkedHashMap<>();
        for (EquityPoint point : curve) {
            LocalDate date = Instant.ofEpochMilli(point.getTimestampMs())
                    .atZone(zoneId).toLocalDate();
            dailyCloseEquity.put(date, point.getEquity());
        }

        List<BigDecimal> returns = new ArrayList<>();
        BigDecimal previousEquity = initCapital;
        for (BigDecimal equity : dailyCloseEquity.values()) {
            BigDecimal pnl = equity.subtract(previousEquity);
            returns.add(previousEquity.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : pnl.divide(previousEquity, SCALE, RM));
            previousEquity = equity;
        }
        return returns;
    }

    private BacktestReport emptyReport(BigDecimal initialCapital) {
        return BacktestReport.builder()
                .initialCapital(initialCapital).finalCapital(initialCapital)
                .totalReturn(BigDecimal.ZERO).annualizedReturn(BigDecimal.ZERO)
                .totalTrades(0).winCount(0).lossCount(0)
                .winRate(BigDecimal.ZERO).maxDrawdown(BigDecimal.ZERO).profitFactor(BigDecimal.ZERO)
                .sharpeRatio(BigDecimal.ZERO).sortinoRatio(BigDecimal.ZERO).dailyStats(List.of())
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RM);
    }

    private void logReport(BacktestReport r) {
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
}
