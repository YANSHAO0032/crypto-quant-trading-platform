package com.quant.execution;

import com.quant.common.model.Order;

/**
 * 交易所客户端接口
 */
public interface ExchangeClient {

    /**
     * 发送订单到交易所
     * @param order 订单
     * @return 交易所订单ID
     */
    String sendOrder(Order order);

    /**
     * 撤销交易所订单
     * @param symbol 交易对
     * @param exchangeOrderId 交易所订单ID
     * @return 是否成功
     */
    boolean cancelOrder(String symbol, String exchangeOrderId);

    /**
     * 查询交易所订单状态
     * @param symbol 交易对
     * @param exchangeOrderId 交易所订单ID
     * @return 订单信息
     */
    Order queryOrder(String symbol, String exchangeOrderId);

    /**
     * 获取交易所名称
     */
    String getExchangeName();
}
