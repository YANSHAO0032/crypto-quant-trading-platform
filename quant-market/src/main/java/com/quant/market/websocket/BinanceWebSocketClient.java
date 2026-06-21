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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private WebSocket webSocket;
    private String currentSymbol;
    private final AtomicBoolean intentionalClose = new AtomicBoolean(false);

    public void connect(String symbol) {
        this.currentSymbol = symbol;
        intentionalClose.set(false);
        doConnect(symbol);
    }

    private void doConnect(String symbol) {
        String url = wsBaseUrl + symbol.toLowerCase() + "@ticker";
        Request request = new Request.Builder().url(url).build();
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("WebSocket已连接: {}", symbol);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    marketDataService.onTickReceived(parseTickData(text));
                } catch (Exception e) {
                    log.error("解析WebSocket消息异常", e);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("WebSocket连接异常: {}", symbol, t);
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("WebSocket已关闭: {} code={} reason={}", symbol, code, reason);
                if (!intentionalClose.get()) {
                    scheduleReconnect();
                }
            }
        });
    }

    private void scheduleReconnect() {
        if (intentionalClose.get() || currentSymbol == null) return;
        log.info("WebSocket将在5秒后重连: {}", currentSymbol);
        reconnectScheduler.schedule(() -> {
            if (!intentionalClose.get()) {
                log.info("WebSocket重连中: {}", currentSymbol);
                doConnect(currentSymbol);
            }
        }, 5, TimeUnit.SECONDS);
    }

    public void disconnect() {
        intentionalClose.set(true);
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
        }
    }

    @PreDestroy
    public void destroy() {
        disconnect();
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
