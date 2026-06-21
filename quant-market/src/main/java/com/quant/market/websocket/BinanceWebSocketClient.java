package com.quant.market.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.quant.common.model.TickData;
import com.quant.market.binance.BinanceMarketDataService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Binance WebSocket 行情客户端。
 * 支持多标的并发订阅，每个 symbol 维护独立的 WebSocket 连接和重连状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWebSocketClient {

    @Value("${binance.ws-url:wss://stream.binance.com:9443/ws/}")
    private String wsBaseUrl;

    private final BinanceMarketDataService marketDataService;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ws-reconnect"));

    /** symbol → 当前 WebSocket 连接 */
    private final Map<String, WebSocket> connections = new ConcurrentHashMap<>();
    /** symbol → 是否主动关闭（防止重连） */
    private final Map<String, AtomicBoolean> intentionalCloseFlags = new ConcurrentHashMap<>();

    /**
     * 连接指定 symbol 的行情 WebSocket（幂等：已连接则跳过）。
     */
    public void connect(String symbol) {
        String normalized = symbol.toLowerCase();
        if (connections.containsKey(normalized)) {
            log.debug("WebSocket已连接，跳过: {}", symbol);
            return;
        }
        intentionalCloseFlags.computeIfAbsent(normalized, k -> new AtomicBoolean(false))
                .set(false);
        doConnect(normalized);
    }

    private void doConnect(String symbol) {
        String url = wsBaseUrl + symbol + "@ticker";
        Request request = new Request.Builder().url(url).build();
        WebSocket ws = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connections.put(symbol, webSocket);
                log.info("WebSocket已连接: {}", symbol);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    marketDataService.onTickReceived(parseTickData(text));
                } catch (Exception e) {
                    log.error("解析WebSocket消息异常: symbol={}", symbol, e);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connections.remove(symbol);
                log.error("WebSocket连接异常: {}", symbol, t);
                scheduleReconnect(symbol);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                connections.remove(symbol);
                log.info("WebSocket已关闭: {} code={} reason={}", symbol, code, reason);
                AtomicBoolean flag = intentionalCloseFlags.get(symbol);
                if (flag == null || !flag.get()) {
                    scheduleReconnect(symbol);
                }
            }
        });
        connections.putIfAbsent(symbol, ws);
    }

    private void scheduleReconnect(String symbol) {
        AtomicBoolean flag = intentionalCloseFlags.get(symbol);
        if (flag != null && flag.get()) return;
        log.info("WebSocket将在5秒后重连: {}", symbol);
        reconnectScheduler.schedule(() -> {
            AtomicBoolean f = intentionalCloseFlags.get(symbol);
            if (f == null || !f.get()) {
                log.info("WebSocket重连中: {}", symbol);
                doConnect(symbol);
            }
        }, 5, TimeUnit.SECONDS);
    }

    /**
     * 断开指定 symbol 的连接。
     */
    public void disconnect(String symbol) {
        String normalized = symbol.toLowerCase();
        AtomicBoolean flag = intentionalCloseFlags.computeIfAbsent(normalized, k -> new AtomicBoolean(false));
        flag.set(true);
        WebSocket ws = connections.remove(normalized);
        if (ws != null) {
            ws.close(1000, "Client disconnect");
        }
    }

    @PreDestroy
    public void destroy() {
        intentionalCloseFlags.values().forEach(f -> f.set(true));
        connections.forEach((symbol, ws) -> ws.close(1000, "Shutdown"));
        connections.clear();
        reconnectScheduler.shutdownNow();
    }

    private TickData parseTickData(String json) {
        JSONObject obj = JSON.parseObject(json);
        return TickData.builder()
                .symbol(obj.getString("s"))
                .interval("TICK")
                .lastPrice(obj.getBigDecimal("c"))
                .bidPrice(obj.getBigDecimal("b"))
                .bidQty(obj.getBigDecimal("B"))
                .askPrice(obj.getBigDecimal("a"))
                .askQty(obj.getBigDecimal("A"))
                .openPrice(obj.getBigDecimal("o"))
                .highPrice(obj.getBigDecimal("h"))
                .lowPrice(obj.getBigDecimal("l"))
                .volume(obj.getBigDecimal("v"))
                .quoteVolume(obj.getBigDecimal("q"))
                .timestamp(obj.getLongValue("E"))
                .receiveTime(LocalDateTime.now())
                .build();
    }
}
