package com.quant.execution.binance;

import com.quant.common.model.Order;
import com.quant.execution.ExchangeClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Binance交易所客户端
 * 负责与Binance REST API交互执行交易
 */
@Slf4j
@Component
public class BinanceExchangeClient implements ExchangeClient {

    private final OkHttpClient httpClient = new OkHttpClient();

    @Value("${binance.api-key:}")
    private String apiKey;

    @Value("${binance.secret-key:}")
    private String secretKey;

    @Value("${binance.base-url:https://api.binance.com}")
    private String baseUrl;

    @Override
    public String sendOrder(Order order) {
        log.info("发送订单到Binance: symbol={}, side={}, type={}, price={}, qty={}",
                order.getSymbol(), order.getSide(), order.getType(), order.getPrice(), order.getQuantity());
        // TODO: 实现Binance下单API调用 POST /api/v3/order，需要HMAC-SHA256签名认证
        return "BINANCE_" + System.currentTimeMillis();
    }

    @Override
    public boolean cancelOrder(String symbol, String exchangeOrderId) {
        log.info("撤销Binance订单: symbol={}, exchangeOrderId={}", symbol, exchangeOrderId);
        // TODO: 实现Binance撤单API调用 DELETE /api/v3/order
        return true;
    }

    @Override
    public Order queryOrder(String symbol, String exchangeOrderId) {
        log.info("查询Binance订单: symbol={}, exchangeOrderId={}", symbol, exchangeOrderId);
        // TODO: 实现Binance查询订单API调用 GET /api/v3/order
        return null;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }
}
