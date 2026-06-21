package com.quant.execution.binance;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.quant.oms.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Binance User Data Stream：监听账户成交、订单状态变更推送。
 * 通过 listenKey 建立私有 WebSocket 连接，接收 executionReport 事件后回调 OrderService。
 *
 * 流程：
 *   1. POST /api/v3/userDataStream → 获取 listenKey
 *   2. 连接 wss://stream.binance.com/ws/{listenKey}
 *   3. 每30分钟 PUT /api/v3/userDataStream 续期 listenKey
 *   4. 收到 executionReport → 判断 FILLED/PARTIALLY_FILLED → 调用 OrderService.onFill/onExitFill
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceUserDataStream {

    @Value("${binance.api-key:}")
    private String apiKey;

    @Value("${binance.base-url:https://api.binance.com}")
    private String baseUrl;

    @Value("${binance.ws-url:wss://stream.binance.com:9443/ws/}")
    private String wsBaseUrl;

    @Value("${binance.user-stream.enabled:false}")
    private boolean enabled;

    private final OkHttpClient httpClient;
    private final BinanceExchangeClient exchangeClient;
    private final OrderService orderService;

    private WebSocket userDataWebSocket;
    private String listenKey;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "user-data-stream"));

    @PostConstruct
    public void start() {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.info("User Data Stream 未启用（binance.user-stream.enabled=false 或 apiKey未配置）");
            return;
        }
        running.set(true);
        listenKey = createListenKey();
        if (listenKey == null) {
            log.error("获取 listenKey 失败，User Data Stream 不启动");
            return;
        }
        connect();
        log.info("Binance User Data Stream 已启动, listenKey={}", listenKey);
    }

    /** 每30分钟续期 listenKey（Binance 默认60分钟过期） */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void renewListenKey() {
        if (!running.get() || listenKey == null) return;
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v3/userDataStream?listenKey=" + listenKey)
                    .addHeader("X-MBX-APIKEY", apiKey)
                    .put(okhttp3.RequestBody.create(new byte[0]))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.debug("listenKey 续期成功");
                } else {
                    log.warn("listenKey 续期失败，将重新获取");
                    listenKey = createListenKey();
                    if (listenKey != null) connect();
                }
            }
        } catch (Exception e) {
            log.error("listenKey 续期异常", e);
        }
    }

    private void connect() {
        String url = wsBaseUrl + listenKey;
        Request request = new Request.Builder().url(url).build();
        userDataWebSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("User Data WebSocket 已连接");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    handleMessage(text);
                } catch (Exception e) {
                    log.error("处理 User Data 消息异常: {}", text, e);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("User Data WebSocket 异常", t);
                if (running.get()) {
                    scheduler.schedule(() -> connect(), 5, TimeUnit.SECONDS);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("User Data WebSocket 关闭: code={}, reason={}", code, reason);
                if (running.get()) {
                    scheduler.schedule(() -> connect(), 5, TimeUnit.SECONDS);
                }
            }
        });
    }

    private void handleMessage(String text) {
        JSONObject event = JSON.parseObject(text);
        String eventType = event.getString("e");

        if ("executionReport".equals(eventType)) {
            String clientOrderId = event.getString("c"); // 对应内部 orderId
            String execType = event.getString("x");      // TRADE / CANCELED 等
            String orderStatus = event.getString("X");   // FILLED / PARTIALLY_FILLED 等
            BigDecimal lastFilledQty = event.getBigDecimal("l");
            BigDecimal lastPrice = event.getBigDecimal("L");
            BigDecimal cumulativeQty = event.getBigDecimal("z");
            BigDecimal cumulativeQuote = event.getBigDecimal("Z");

            BigDecimal avgPrice = cumulativeQty != null && cumulativeQty.compareTo(BigDecimal.ZERO) > 0
                    && cumulativeQuote != null
                    ? cumulativeQuote.divide(cumulativeQty, 8, java.math.RoundingMode.HALF_UP)
                    : lastPrice;

            if ("TRADE".equals(execType)) {
                if ("FILLED".equals(orderStatus) || "PARTIALLY_FILLED".equals(orderStatus)) {
                    log.info("收到成交推送: orderId={}, status={}, qty={}, avgPrice={}",
                            clientOrderId, orderStatus, cumulativeQty, avgPrice);
                    // 简化处理：所有成交走 onFill，平仓区分依赖上层策略逻辑
                    orderService.onFill(clientOrderId, cumulativeQty, avgPrice);
                }
            } else if ("CANCELED".equals(execType)) {
                log.info("收到撤单推送: orderId={}", clientOrderId);
            }
        } else if ("outboundAccountPosition".equals(eventType)) {
            log.debug("账户余额变更推送: {}", text);
        }
    }

    private String createListenKey() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v3/userDataStream")
                    .addHeader("X-MBX-APIKEY", apiKey)
                    .post(okhttp3.RequestBody.create(new byte[0]))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() == null) return null;
                String body = response.body().string();
                return JSON.parseObject(body).getString("listenKey");
            }
        } catch (Exception e) {
            log.error("创建 listenKey 失败", e);
            return null;
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (userDataWebSocket != null) {
            userDataWebSocket.close(1000, "Shutdown");
        }
        scheduler.shutdownNow();
    }
}
