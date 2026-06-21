package com.quant.market.binance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.model.TickData;
import com.quant.market.MarketDataService;
import com.quant.market.TickReceivedEvent;
import com.quant.market.mapper.KlineRangeMapper;
import com.quant.market.mapper.TickDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Binance行情数据服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceMarketDataService implements MarketDataService {

    private static final String DEFAULT_INTERVAL = "TICK";

    private final TickDataMapper tickDataMapper;
    private final KlineRangeMapper klineRangeMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, Consumer<TickData>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, TickData> latestTicks = new ConcurrentHashMap<>();

    @Override
    public void subscribe(String symbol, Consumer<TickData> callback) {
        subscribers.put(normalizeSymbol(symbol), callback);
        log.info("订阅行情: {}", symbol);
    }

    @Override
    public void unsubscribe(String symbol) {
        subscribers.remove(normalizeSymbol(symbol));
        log.info("取消订阅行情: {}", symbol);
    }

    @Override
    public TickData getLatestTick(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        TickData cached = latestTicks.get(normalizedSymbol);
        if (cached != null) return cached;
        return tickDataMapper.selectOne(new LambdaQueryWrapper<TickData>()
                .eq(TickData::getSymbol, normalizedSymbol)
                .orderByDesc(TickData::getTimestamp)
                .last("LIMIT 1"));
    }

    @Override
    public List<TickData> getHistoricalKlines(String symbol, String interval, int limit) {
        log.info("获取历史K线: symbol={}, interval={}, limit={}", symbol, interval, limit);
        if (limit <= 0) return List.of();

        List<TickData> data = tickDataMapper.selectList(new LambdaQueryWrapper<TickData>()
                .eq(TickData::getSymbol, normalizeSymbol(symbol))
                .eq(TickData::getInterval, normalizeInterval(interval))
                .orderByDesc(TickData::getTimestamp)
                .last("LIMIT " + limit));
        List<TickData> chronological = new ArrayList<>(data);
        Collections.reverse(chronological);
        return chronological;
    }

    /**
     * 处理收到的Tick数据：持久化 + 内存缓存 + 回调分发 + 发布心跳事件（供超时检测）。
     */
    public void onTickReceived(TickData tick) {
        String normalizedSymbol = normalizeSymbol(tick.getSymbol());
        tick.setSymbol(normalizedSymbol);
        tick.setInterval(normalizeInterval(tick.getInterval()));
        tickDataMapper.insert(tick);
        klineRangeMapper.upsertRange(normalizedSymbol, tick.getInterval(), tick.getTimestamp());

        latestTicks.put(normalizedSymbol, tick);

        // 发布事件，供 ProductionScheduler 更新心跳时间（解耦，避免循环依赖）
        eventPublisher.publishEvent(new TickReceivedEvent(this, tick));

        Consumer<TickData> callback = subscribers.get(normalizedSymbol);
        if (callback != null) {
            try {
                callback.accept(tick);
            } catch (Exception e) {
                log.error("处理Tick回调异常: symbol={}", normalizedSymbol, e);
            }
        }
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.toUpperCase();
    }

    private String normalizeInterval(String interval) {
        return interval == null || interval.isBlank() ? DEFAULT_INTERVAL : interval.toUpperCase();
    }
}
