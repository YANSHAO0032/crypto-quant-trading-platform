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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Binance行情数据服务实现。
 * subscribers 使用 Map<symbol, List<Consumer>> 支持同一标的多策略订阅。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceMarketDataService implements MarketDataService {

    private static final String DEFAULT_INTERVAL = "TICK";

    private final TickDataMapper tickDataMapper;
    private final KlineRangeMapper klineRangeMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** symbol → 订阅回调列表（CopyOnWriteArrayList保证并发安全） */
    private final Map<String, CopyOnWriteArrayList<Consumer<TickData>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, TickData> latestTicks = new ConcurrentHashMap<>();

    @Override
    public void subscribe(String symbol, Consumer<TickData> callback) {
        subscribers.computeIfAbsent(normalizeSymbol(symbol), k -> new CopyOnWriteArrayList<>())
                .add(callback);
        log.info("订阅行情: {}, 当前订阅数={}", symbol,
                subscribers.getOrDefault(normalizeSymbol(symbol), new CopyOnWriteArrayList<>()).size());
    }

    @Override
    public void unsubscribe(String symbol) {
        List<Consumer<TickData>> callbacks = subscribers.remove(normalizeSymbol(symbol));
        log.info("取消所有订阅行情: {}, 移除回调数={}", symbol, callbacks == null ? 0 : callbacks.size());
    }

    /** 取消特定回调（用于单策略停止，不影响同标的其他策略） */
    public void unsubscribe(String symbol, Consumer<TickData> callback) {
        List<Consumer<TickData>> callbacks = subscribers.get(normalizeSymbol(symbol));
        if (callbacks != null) {
            callbacks.remove(callback);
            log.info("取消单个行情订阅: {}, 剩余订阅数={}", symbol, callbacks.size());
        }
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
     * 处理收到的Tick数据：持久化 + 内存缓存 + 广播给所有订阅者 + 发布心跳事件。
     */
    public void onTickReceived(TickData tick) {
        String normalizedSymbol = normalizeSymbol(tick.getSymbol());
        tick.setSymbol(normalizedSymbol);
        tick.setInterval(normalizeInterval(tick.getInterval()));
        tickDataMapper.insert(tick);
        klineRangeMapper.upsertRange(normalizedSymbol, tick.getInterval(), tick.getTimestamp());

        latestTicks.put(normalizedSymbol, tick);

        eventPublisher.publishEvent(new TickReceivedEvent(this, tick));

        List<Consumer<TickData>> callbacks = subscribers.get(normalizedSymbol);
        if (callbacks != null) {
            for (Consumer<TickData> callback : callbacks) {
                try {
                    callback.accept(tick);
                } catch (Exception e) {
                    log.error("处理Tick回调异常: symbol={}", normalizedSymbol, e);
                }
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
