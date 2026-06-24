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
import org.springframework.beans.factory.annotation.Value;
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
    private final PositionSizer positionSizer;
    private final DataCoverageValidator dataCoverageValidator = new DataCoverageValidator();

    @Value("${strategy.order-quantity:0.001}")
    private BigDecimal orderQuantity = new BigDecimal("0.001");

    @Value("${backtest.fee-rate:0.001}")
    private BigDecimal feeRate = new BigDecimal("0.001");

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport runKlineBacktest(Strategy strategy, String symbol, String interval,
                                           long startMs, long endMs, BigDecimal initialCapital) {
        return runKlineBacktest(strategy, symbol, interval, startMs, endMs, initialCapital, defaultBacktestConfig());
    }

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport runKlineBacktest(Strategy strategy, String symbol, String interval,
                                           long startMs, long endMs, BigDecimal initialCapital,
                                           BacktestConfig config) {
        BacktestConfig effectiveConfig = normalizeConfig(config);
        List<Kline> klines = dataLoader.loadKlines(symbol, interval, startMs, endMs);
        if (klines.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("kline_%s interval=%s [%d,%d] has no data",
                            symbol.toLowerCase(), interval, startMs, endMs));
        }
        DataCoverageValidator.Coverage coverage = dataCoverageValidator.validate(
                interval,
                startMs,
                endMs,
                klines.get(0).getOpenTime(),
                klines.get(klines.size() - 1).getCloseTime(),
                effectiveConfig.isAllowPartialData());
        return runOnKlines(strategy, symbol, interval, klines, initialCapital, effectiveConfig,
                coverage, startMs, endMs);
    }

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport quickKlineBacktest(Strategy strategy, String symbol, String interval,
                                             int limit, BigDecimal initialCapital) {
        List<Kline> klines = dataLoader.loadLatestKlines(symbol, interval, limit);
        if (klines.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("kline_%s interval=%s has no data", symbol.toLowerCase(), interval));
        }
        return runOnKlines(strategy, symbol, interval, klines, initialCapital, defaultBacktestConfig(),
                null, null, null);
    }

    private BacktestReport runOnKlines(Strategy strategy, String symbol, String interval,
                                       List<Kline> klines, BigDecimal initialCapital,
                                       BacktestConfig config,
                                       DataCoverageValidator.Coverage coverage,
                                       Long requestedStartMs,
                                       Long requestedEndMs) {
        String backtestId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startTime = LocalDateTime.now();

        log.info("start kline backtest: backtestId={}, strategy={}, symbol={}, interval={}, dataSize={}, capital={}",
                backtestId, strategy.getStrategyName(), symbol, interval, klines.size(), initialCapital);

        strategy.init();
        BacktestAccount account = new BacktestAccount(initialCapital, config.getFeeRate());
        account.markInitialEquity(MarketTimeNormalizer.toEpochMillis(klines.get(0).getOpenTime()));
        List<BacktestTradeRecord> tradeRecords = new ArrayList<>();
        int sequenceNo = 1;

        for (Kline kline : klines) {
            long tickTimestamp = MarketTimeNormalizer.toEpochMillis(kline.getOpenTime());
            long eventTimestamp = MarketTimeNormalizer.toEpochMillis(kline.getCloseTime());
            BigDecimal price = kline.getClosePrice();

            TickData tick = TickData.builder()
                    .symbol(symbol)
                    .interval(interval)
                    .openPrice(kline.getOpenPrice())
                    .highPrice(kline.getHighPrice())
                    .lowPrice(kline.getLowPrice())
                    .lastPrice(price)
                    .volume(kline.getVolume())
                    .quoteVolume(kline.getQuoteVolume())
                    .timestamp(tickTimestamp)
                    .build();

            Signal signal = nextSignal(strategy, tick);
            if (signal != Signal.HOLD) {
                BigDecimal quantity = positionSizer.size(signal, account, price, config);
                account.execute(signal, price, quantity, eventTimestamp);
                tradeRecords.add(toTradeRecord(backtestId, sequenceNo++, signal, price, eventTimestamp));
            }
            account.markToMarket(price, eventTimestamp);
        }

        strategy.stop();

        LocalDateTime endTime = LocalDateTime.now();
        log.info("kline backtest completed: backtestId={}, fills={}", backtestId, tradeRecords.size());

        BacktestReport report = performanceAnalyzer.analyze(account.toResult(), config.getTimezone());
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
        applyAuditFields(report, config, account, coverage, requestedStartMs, requestedEndMs);

        persistReport(report);
        persistTradeRecords(tradeRecords);
        return report;
    }

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport runBacktest(Strategy strategy, List<TickData> historicalData, BigDecimal initialCapital) {
        String backtestId = UUID.randomUUID().toString().replace("-", "");
        String symbol = historicalData.isEmpty() ? null : historicalData.get(0).getSymbol();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("start backtest: backtestId={}, strategy={}, dataSize={}, capital={}",
                backtestId, strategy.getStrategyName(), historicalData.size(), initialCapital);

        strategy.init();
        BacktestConfig config = defaultBacktestConfig();
        BacktestAccount account = new BacktestAccount(initialCapital, config.getFeeRate());
        if (!historicalData.isEmpty()) {
            account.markInitialEquity(MarketTimeNormalizer.toEpochMillis(historicalData.get(0).getTimestamp()));
        }
        List<BacktestTradeRecord> tradeRecords = new ArrayList<>();
        int sequenceNo = 1;

        for (TickData tick : historicalData) {
            long eventTimestamp = MarketTimeNormalizer.toEpochMillis(tick.getTimestamp());
            tick.setTimestamp(eventTimestamp);
            BigDecimal price = tick.getLastPrice();

            Signal signal = nextSignal(strategy, tick);
            if (signal != Signal.HOLD) {
                BigDecimal quantity = positionSizer.size(signal, account, price, config);
                account.execute(signal, price, quantity, eventTimestamp);
                tradeRecords.add(toTradeRecord(backtestId, sequenceNo++, signal, price, eventTimestamp));
            }
            account.markToMarket(price, eventTimestamp);
        }

        strategy.stop();
        LocalDateTime endTime = LocalDateTime.now();

        BacktestReport report = performanceAnalyzer.analyze(account.toResult(), config.getTimezone());
        report.setBacktestId(backtestId);
        report.setStrategyId(strategy.getStrategyId());
        report.setStrategyName(strategy.getStrategyName());
        report.setSymbol(symbol);
        report.setDataCount(historicalData.size());
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        applyAuditFields(report, config, account, null, null, null);

        persistReport(report);
        persistTradeRecords(tradeRecords);
        return report;
    }

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport quickBacktest(Strategy strategy, String symbol, int dataCount,
                                        BigDecimal startPrice, BigDecimal initialCapital) {
        List<TickData> data = dataLoader.loadFromDb(symbol, "1m", dataCount);
        if (data.isEmpty()) {
            log.info("tick_data has no historical data, using generated mock data: symbol={}, count={}", symbol, dataCount);
            data = dataLoader.generateMockData(symbol, dataCount, startPrice);
        }
        return runBacktest(strategy, data, initialCapital);
    }

    @Transactional(rollbackFor = Exception.class)
    public BacktestReport csvBacktest(Strategy strategy, String csvFilePath, String symbol, BigDecimal initialCapital) {
        List<TickData> data = dataLoader.loadFromCsv(csvFilePath, symbol);
        if (data.isEmpty()) {
            throw new IllegalArgumentException("CSV has no valid data: " + csvFilePath);
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

    private BacktestConfig defaultBacktestConfig() {
        return BacktestConfig.builder()
                .sizingMode(PositionSizingMode.FIXED_QTY)
                .orderQuantity(orderQuantity)
                .feeRate(feeRate)
                .allowPartialData(false)
                .timezone("Asia/Shanghai")
                .build();
    }

    private BacktestConfig normalizeConfig(BacktestConfig config) {
        BacktestConfig defaults = defaultBacktestConfig();
        if (config == null) {
            return defaults;
        }
        return BacktestConfig.builder()
                .sizingMode(config.getSizingMode() == null ? defaults.getSizingMode() : config.getSizingMode())
                .orderQuantity(config.getOrderQuantity() == null ? defaults.getOrderQuantity() : config.getOrderQuantity())
                .orderNotional(config.getOrderNotional())
                .equityPercent(config.getEquityPercent())
                .feeRate(config.getFeeRate() == null ? defaults.getFeeRate() : config.getFeeRate())
                .allowPartialData(config.isAllowPartialData())
                .timezone(config.getTimezone() == null || config.getTimezone().isBlank()
                        ? defaults.getTimezone() : config.getTimezone())
                .build();
    }

    private void applyAuditFields(BacktestReport report, BacktestConfig config, BacktestAccount account,
                                  DataCoverageValidator.Coverage coverage,
                                  Long requestedStartMs, Long requestedEndMs) {
        report.setRequestedStartMs(requestedStartMs);
        report.setRequestedEndMs(requestedEndMs);
        if (coverage != null) {
            report.setCoverageComplete(coverage.coverageComplete());
            report.setMissingBars(coverage.missingBars());
            report.setCoverageMessage(coverage.message());
        }
        report.setSizingMode(config.getSizingMode().name());
        report.setOrderQuantity(config.getOrderQuantity());
        report.setOrderNotional(config.getOrderNotional());
        report.setEquityPercent(config.getEquityPercent());
        report.setFeeRate(config.getFeeRate());
        report.setTotalFee(account.getTotalFee());
        report.setRejectedOrders(0);
    }

    private Signal nextSignal(Strategy strategy, TickData tick) {
        Signal signal = strategy.onTick(tick);
        return signal == Signal.HOLD ? strategy.onCheckExit(tick) : signal;
    }

    private BacktestTradeRecord toTradeRecord(String backtestId, int sequenceNo,
                                              Signal signal, BigDecimal price, long timestampMs) {
        return BacktestTradeRecord.builder()
                .backtestId(backtestId)
                .sequenceNo(sequenceNo)
                .signal(signal)
                .price(price)
                .timestamp(timestampMs)
                .build();
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
                .requestedStartMs(report.getRequestedStartMs())
                .requestedEndMs(report.getRequestedEndMs())
                .coverageComplete(report.getCoverageComplete())
                .missingBars(report.getMissingBars())
                .coverageMessage(report.getCoverageMessage())
                .dataCount(report.getDataCount())
                .sizingMode(report.getSizingMode())
                .orderQuantity(report.getOrderQuantity())
                .orderNotional(report.getOrderNotional())
                .equityPercent(report.getEquityPercent())
                .feeRate(report.getFeeRate())
                .totalFee(report.getTotalFee())
                .rejectedOrders(report.getRejectedOrders())
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
