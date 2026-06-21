package com.quant.oms;

import com.quant.common.model.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 订单管理抽象接口：统一回测和实盘的订单操作，对应 banbot IOrderMgr 设计。
 * 回测实现：BacktestOrderManager（模拟成交，不触碰交易所）
 * 实盘实现：OrderManager（真实下单，走 ExecutionService）
 */
public interface IOrderManager {

    Order createOrder(Order order);

    boolean submitOrder(String orderId);

    boolean cancelOrder(String orderId);

    Optional<Order> getOrder(String orderId);

    List<Order> getOrdersByStrategy(String strategyId);

    List<Order> getAllOrders();
}
