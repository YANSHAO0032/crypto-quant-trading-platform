package com.quant.execution.binance;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.quant.common.enums.OrderStatus;
import com.quant.common.model.Order;
import com.quant.execution.ExchangeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Binance REST API 客户端，实现 HMAC-SHA256 签名认证。
 * 参考 Binance API 文档: https://binance-docs.github.io/apidocs/spot/en/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceExchangeClient implements ExchangeClient {

    private final OkHttpClient httpClient;

    @Value("${binance.api-key:}")
    private String apiKey;

    @Value("${binance.secret-key:}")
    private String secretKey;

    @Value("${binance.base-url:https://api.binance.com}")
    private String baseUrl;

    // ========== ExchangeClient 实现 ==========

    @Override
    public String sendOrder(Order order) {
        Map<String, String> params = new HashMap<>();
        params.put("symbol", order.getSymbol());
        params.put("side", order.getSide().name());
        params.put("type", order.getType().name());
        params.put("price", order.getPrice().toPlainString());
        params.put("quantity", order.getQuantity().toPlainString());
        params.put("timeInForce", "GTC");
        params.put("newClientOrderId", order.getOrderId());
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));

        try {
            String responseBody = post("/api/v3/order", params);
            JSONObject json = JSON.parseObject(responseBody);
            if (json.containsKey("code")) {
                log.error("Binance下单失败: code={}, msg={}", json.getInteger("code"), json.getString("msg"));
                return null;
            }
            String exchangeOrderId = json.getString("orderId");
            log.info("Binance下单成功: symbol={}, side={}, exchangeOrderId={}", order.getSymbol(), order.getSide(), exchangeOrderId);
            return exchangeOrderId;
        } catch (Exception e) {
            log.error("Binance下单异常: orderId={}", order.getOrderId(), e);
            return null;
        }
    }

    @Override
    public boolean cancelOrder(String symbol, String exchangeOrderId) {
        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("orderId", exchangeOrderId);
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));

        try {
            String responseBody = delete("/api/v3/order", params);
            JSONObject json = JSON.parseObject(responseBody);
            if (json.containsKey("code")) {
                log.error("Binance撤单失败: code={}, msg={}", json.getInteger("code"), json.getString("msg"));
                return false;
            }
            log.info("Binance撤单成功: symbol={}, exchangeOrderId={}", symbol, exchangeOrderId);
            return true;
        } catch (Exception e) {
            log.error("Binance撤单异常: symbol={}, orderId={}", symbol, exchangeOrderId, e);
            return false;
        }
    }

    @Override
    public Order queryOrder(String symbol, String exchangeOrderId) {
        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("orderId", exchangeOrderId);
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));

        try {
            String responseBody = get("/api/v3/order", params);
            JSONObject json = JSON.parseObject(responseBody);
            if (json.containsKey("code")) {
                log.error("Binance查询订单失败: code={}, msg={}", json.getInteger("code"), json.getString("msg"));
                return null;
            }
            Order order = new Order();
            order.setExchangeOrderId(json.getString("orderId"));
            order.setSymbol(json.getString("symbol"));
            order.setFilledQuantity(json.getBigDecimal("executedQty"));
            order.setAvgFilledPrice(json.getBigDecimal("cummulativeQuoteQty")
                    .compareTo(BigDecimal.ZERO) > 0 && json.getBigDecimal("executedQty").compareTo(BigDecimal.ZERO) > 0
                    ? json.getBigDecimal("cummulativeQuoteQty").divide(json.getBigDecimal("executedQty"), 8, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            order.setStatus(mapBinanceStatus(json.getString("status")));
            return order;
        } catch (Exception e) {
            log.error("Binance查询订单异常: symbol={}, orderId={}", symbol, exchangeOrderId, e);
            return null;
        }
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    /**
     * 获取账户各资产余额，用于初始化 WalletManager。
     * 返回 Map<asset, available>（仅 free 余额）。
     */
    public Map<String, BigDecimal> getAccountBalances() {
        Map<String, String> params = new HashMap<>();
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));

        Map<String, BigDecimal> balances = new HashMap<>();
        try {
            String responseBody = get("/api/v3/account", params);
            JSONObject json = JSON.parseObject(responseBody);
            if (json.containsKey("code")) {
                log.error("获取Binance账户余额失败: code={}, msg={}", json.getInteger("code"), json.getString("msg"));
                return balances;
            }
            JSONArray balanceArray = json.getJSONArray("balances");
            for (int i = 0; i < balanceArray.size(); i++) {
                JSONObject b = balanceArray.getJSONObject(i);
                BigDecimal free = b.getBigDecimal("free");
                if (free != null && free.compareTo(BigDecimal.ZERO) > 0) {
                    balances.put(b.getString("asset"), free);
                }
            }
            log.info("获取Binance账户余额成功, 资产数={}", balances.size());
        } catch (Exception e) {
            log.error("获取Binance账户余额异常", e);
        }
        return balances;
    }

    // ========== HTTP 工具方法 ==========

    private String get(String path, Map<String, String> params) throws IOException {
        String query = buildSignedQuery(params);
        Request request = new Request.Builder()
                .url(baseUrl + path + "?" + query)
                .addHeader("X-MBX-APIKEY", apiKey)
                .get()
                .build();
        return execute(request);
    }

    private String post(String path, Map<String, String> params) throws IOException {
        String query = buildSignedQuery(params);
        RequestBody body = RequestBody.create(query.getBytes(StandardCharsets.UTF_8),
                okhttp3.MediaType.parse("application/x-www-form-urlencoded"));
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("X-MBX-APIKEY", apiKey)
                .post(body)
                .build();
        return execute(request);
    }

    private String delete(String path, Map<String, String> params) throws IOException {
        String query = buildSignedQuery(params);
        Request request = new Request.Builder()
                .url(baseUrl + path + "?" + query)
                .addHeader("X-MBX-APIKEY", apiKey)
                .delete()
                .build();
        return execute(request);
    }

    private String execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                log.warn("Binance API响应非200: status={}, body={}", response.code(), body);
            }
            return body;
        }
    }

    private String buildSignedQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(k).append('=').append(v);
        });
        String signature = hmacSha256(secretKey, sb.toString());
        sb.append("&signature=").append(signature);
        return sb.toString();
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256签名失败", e);
        }
    }

    private OrderStatus mapBinanceStatus(String binanceStatus) {
        return switch (binanceStatus) {
            case "NEW" -> OrderStatus.SUBMITTED;
            case "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrderStatus.FILLED;
            case "CANCELED" -> OrderStatus.CANCELLED;
            case "REJECTED", "EXPIRED" -> OrderStatus.REJECTED;
            default -> OrderStatus.PENDING;
        };
    }
}
