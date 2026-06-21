package com.quant.oms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.model.Order;
import com.quant.oms.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 订单仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final OrderMapper orderMapper;

    public void save(Order order) {
        if (orderMapper.selectById(order.getOrderId()) == null) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orderMapper.selectById(orderId));
    }

    public Optional<Order> findByExchangeOrderId(String exchangeOrderId) {
        if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getExchangeOrderId, exchangeOrderId)
                .last("LIMIT 1")));
    }

    public List<Order> findBySymbol(String symbol) {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getSymbol, symbol)
                .orderByDesc(Order::getCreateTime));
    }

    public List<Order> findByStrategyId(String strategyId) {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStrategyId, strategyId)
                .orderByDesc(Order::getCreateTime));
    }

    public List<Order> findAll() {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .orderByDesc(Order::getCreateTime));
    }

    public void deleteById(String orderId) {
        orderMapper.deleteById(orderId);
    }
}
