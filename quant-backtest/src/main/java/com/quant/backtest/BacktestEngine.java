package com.quant.backtest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.backtest.mapper.BacktestReportMapper;
import com.quant.backtest.mapper.BacktestTradeRecordMapper;
import com.quant.common.enums.Signal;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.BacktestTradeRecord;
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
        log.info("回测完成: backtestId={}, 产生 {} 个交易信号", backtestId, tradeRecords.size());

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

    /**
     * 使用数据库中的真实历史数据做回测；若DB无数据则降级为模拟数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public BacktestReport quickBacktest(Strategy strategy, String symbol, int dataCount,
                                        BigDecimal startPrice, BigDecimal initialCapital) {
        List<TickData> data = dataLoader.loadFromDb(symbol, "1m", dataCount);
        if (data.isEmpty()) {
            log.info("数据库无历史数据，降级使用模拟数据: symbol={}, count={}", symbol, dataCount);
            data = dataLoader.generateMockData(symbol, dataCount, startPrice);
        }
        return runBacktest(strategy, data, initialCapital);
    }

    /**
     * 使用 CSV 文件数据做回测。
     */
    @Transactional(rollbackFor = Exception.class)
    public BacktestReport csvBacktest(Strategy strategy, String csvFilePath, String symbol, BigDecimal initialCapital) {
        List<TickData> data = dataLoader.loadFromCsv(csvFilePath, symbol);
        if (data.isEmpty()) {
            throw new IllegalArgumentException("CSV文件无有效数据: " + csvFilePath);
        }
        return runBacktest(strategy, data, initialCapital);
    }

    /**
     * 查询历史回测报告列表。
     */
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
