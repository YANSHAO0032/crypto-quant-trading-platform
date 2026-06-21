package com.quant.backtest;

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

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport quickBacktest(Strategy strategy, String symbol, int dataCount,
                                        BigDecimal startPrice, BigDecimal initialCapital) {
        List<TickData> mockData = dataLoader.generateMockData(symbol, dataCount, startPrice);
        return runBacktest(strategy, mockData, initialCapital);
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
