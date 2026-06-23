package com.quant.backtest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.backtest.mapper.BacktestReportMapper;
import com.quant.backtest.mapper.BacktestTradeRecordMapper;
import com.quant.common.enums.Signal;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.BacktestTradeRecord;
import com.quant.common.model.Kline;
import com.quant.common.model.TickData;
import com.quant.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BacktestEngine {

    private final PerformanceAnalyzer performanceAnalyzer;
    private final DataLoader dataLoader;
    private final BacktestReportMapper backtestReportMapper;
    private final BacktestTradeRecordMapper backtestTradeRecordMapper;

    /**
     * 基于 kline_{symbol} 分表的标准回测入口（推荐）。
     *
     * @param symbol    交易对，如 BTCUSDT
     * @param interval  K线周期，如 1m/1h/1d
     * @param startMs   回测起始时间戳(ms)
     * @param endMs     回测结束时间戳(ms)
     */
    @Transactional(rollbackFor = Exception.class)
    public BacktestReport runKlineBacktest(Strategy strategy, String symbol, String interval,
                                           long startMs, long endMs, BigDecimal initialCapital) {
        List<Kline> klines = dataLoader.loadKlines(symbol, interval, startMs, endMs);
        if (klines.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("kline_%s 中 interval=%s [%d,%d] 无数据，请先导入历史K线",
                            symbol.toLowerCase(), interval, startMs, endMs));
        }
        return runOnKlines(strategy, symbol, interval, klines, initialCapital);
    }

    /**
     * 使用 kline 分表中最新 N 根 K 线做快速回测（无需指定时间范围）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BacktestReport quickKlineBacktest(Strategy strategy, String symbol, String interval,
                                             int limit, BigDecimal initialCapital) {
        List<Kline> klines = dataLoader.loadLatestKlines(symbol, interval, limit);
        if (klines.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("kline_%s 中 interval=%s 无数据，请先导入历史K线",
                            symbol.toLowerCase(), interval));
        }
        return runOnKlines(strategy, symbol, interval, klines, initialCapital);
    }

    private BacktestReport runOnKlines(Strategy strategy, String symbol, String interval,
                                       List<Kline> klines, BigDecimal initialCapital) {
        String backtestId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startTime = LocalDateTime.now();

        log.info("开始回测(K线): backtestId={}, strategy={}, symbol={}, interval={}, dataSize={}, capital={}",
                backtestId, strategy.getStrategyName(), symbol, interval, klines.size(), initialCapital);

        strategy.init();

        List<BacktestTradeRecord> tradeRecords = new ArrayList<>();
        int sequenceNo = 1;

        for (Kline kline : klines) {
            // 将 Kline 转为 TickData 供策略消费（策略接口复用，无需改 Strategy）
            TickData tick = TickData.builder()
                    .symbol(symbol)
                    .interval(interval)
                    .openPrice(kline.getOpenPrice())
                    .highPrice(kline.getHighPrice())
                    .lowPrice(kline.getLowPrice())
                    .lastPrice(kline.getClosePrice())
                    .volume(kline.getVolume())
                    .quoteVolume(kline.getQuoteVolume())
                    .timestamp(kline.getOpenTime())
                    .build();

            Signal signal = strategy.onTick(tick);
            if (signal != Signal.HOLD) {
                tradeRecords.add(BacktestTradeRecord.builder()
                        .backtestId(backtestId)
                        .sequenceNo(sequenceNo++)
                        .signal(signal)
                        .price(kline.getClosePrice())
                        .timestamp(kline.getCloseTime())
                        .build());
            }
        }

        strategy.stop();

        LocalDateTime endTime = LocalDateTime.now();
        log.info("回测完成: backtestId={}, 信号数={}", backtestId, tradeRecords.size());

        BacktestReport report = performanceAnalyzer.analyze(tradeRecords, initialCapital);
        report.setBacktestId(backtestId);
        report.setStrategyId(strategy.getStrategyId());
        report.setStrategyName(strategy.getStrategyName());
        report.setSymbol(symbol);
        report.setInterval(interval);
        report.setRangeStartMs(klines.get(0).getOpenTime());
        report.setRangeEndMs(klines.get(klines.size() - 1).getCloseTime());
        report.setDataCount(klines.size());
        report.setStartTime(startTime);
        report.setEndTime(endTime);

        persistReport(report);
        persistTradeRecords(tradeRecords);
        return report;
    }

    // ========== 旧接口保留，兼容 tick_data 表数据 ==========

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport runBacktest(Strategy strategy, List<TickData> historicalData, BigDecimal initialCapital) {
        String backtestId = UUID.randomUUID().toString().replace("-", "");
        String symbol = historicalData.isEmpty() ? null : historicalData.get(0).getSymbol();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("开始回测: backtestId={}, strategy={}, dataSize={}, capital={}",
                backtestId, strategy.getStrategyName(), historicalData.size(), initialCapital);

        strategy.init();

        List<BacktestTradeRecord> tradeRecords = new ArrayList<>();
        int sequenceNo = 1;

        for (TickData tick : historicalData) {
            Signal signal = strategy.onTick(tick);
            if (signal != Signal.HOLD) {
                tradeRecords.add(BacktestTradeRecord.builder()
                        .backtestId(backtestId)
                        .sequenceNo(sequenceNo++)
                        .signal(signal)
                        .price(tick.getLastPrice())
                        .timestamp(tick.getTimestamp())
                        .build());
            }
        }

        strategy.stop();

        LocalDateTime endTime = LocalDateTime.now();

        BacktestReport report = performanceAnalyzer.analyze(tradeRecords, initialCapital);
        report.setBacktestId(backtestId);
        report.setStrategyId(strategy.getStrategyId());
        report.setStrategyName(strategy.getStrategyName());
        report.setSymbol(symbol);
        report.setDataCount(historicalData.size());
        report.setStartTime(startTime);
        report.setEndTime(endTime);

        persistReport(report);
        persistTradeRecords(tradeRecords);
        return report;
    }

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport quickBacktest(Strategy strategy, String symbol, int dataCount,
                                        BigDecimal startPrice, BigDecimal initialCapital) {
        List<TickData> data = dataLoader.loadFromDb(symbol, "1m", dataCount);
        if (data.isEmpty()) {
            log.info("tick_data无历史数据，降级使用模拟数据: symbol={}, count={}", symbol, dataCount);
            data = dataLoader.generateMockData(symbol, dataCount, startPrice);
        }
        return runBacktest(strategy, data, initialCapital);
    }

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport csvBacktest(Strategy strategy, String csvFilePath, String symbol, BigDecimal initialCapital) {
        List<TickData> data = dataLoader.loadFromCsv(csvFilePath, symbol);
        if (data.isEmpty()) {
            throw new IllegalArgumentException("CSV文件无有效数据: " + csvFilePath);
        }
        return runBacktest(strategy, data, initialCapital);
    }

    public List<BacktestReport> listReports(String strategyId, int limit) {
        LambdaQueryWrapper<BacktestReport> wrapper = new LambdaQueryWrapper<BacktestReport>()
                .orderByDesc(BacktestReport::getCreateTime);
        if (strategyId != null && !strategyId.isBlank()) {
            wrapper.eq(BacktestReport::getStrategyId, strategyId);
        }
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return backtestReportMapper.selectList(wrapper);
    }

    private void persistReport(BacktestReport report) {
        BacktestReport entity = BacktestReport.builder()
                .backtestId(report.getBacktestId())
                .strategyId(report.getStrategyId())
                .strategyName(report.getStrategyName())
                .symbol(report.getSymbol())
                .interval(report.getInterval())
                .rangeStartMs(report.getRangeStartMs())
                .rangeEndMs(report.getRangeEndMs())
                .dataCount(report.getDataCount())
                .initialCapital(report.getInitialCapital())
                .finalCapital(report.getFinalCapital())
                .totalReturn(report.getTotalReturn())
                .annualizedReturn(report.getAnnualizedReturn())
                .totalTrades(report.getTotalTrades())
                .winCount(report.getWinCount())
                .lossCount(report.getLossCount())
                .winRate(report.getWinRate())
                .maxDrawdown(report.getMaxDrawdown())
                .profitFactor(report.getProfitFactor())
                .sharpeRatio(report.getSharpeRatio())
                .sortinoRatio(report.getSortinoRatio())
                .startTime(report.getStartTime())
                .endTime(report.getEndTime())
                .createTime(LocalDateTime.now())
                .build();
        backtestReportMapper.insert(entity);
    }

    private void persistTradeRecords(List<BacktestTradeRecord> tradeRecords) {
        for (BacktestTradeRecord record : tradeRecords) {
            backtestTradeRecordMapper.insert(record);
        }
    }
}
