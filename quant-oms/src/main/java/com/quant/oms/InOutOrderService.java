package com.quant.oms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.model.InOutOrder;
import com.quant.common.model.Order;
import com.quant.oms.mapper.InOutOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 进出一体订单服务：将开仓和平仓订单配对，记录单笔完整交易的盈亏。
 * 对应 banbot InOutOrder 设计，是实现真实 PnL 统计的关键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InOutOrderService {

    private final InOutOrderMapper inOutOrderMapper;

    /**
     * 开仓成交时创建 InOutOrder 记录。
     */
    public void recordOpen(Order order) {
        InOutOrder record = InOutOrder.builder()
                .enterOrderId(order.getOrderId())
                .strategyId(order.getStrategyId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .enterPrice(order.getAvgFilledPrice())
                .enterQty(order.getFilledQuantity())
                .enterTime(LocalDateTime.now())
                .closed(false)
                .createTime(LocalDateTime.now())
                .build();
        inOutOrderMapper.insert(record);
        log.info("开仓记录: orderId={}, symbol={}, side={}, price={}, qty={}",
                order.getOrderId(), order.getSymbol(), order.getSide(),
                order.getAvgFilledPrice(), order.getFilledQuantity());
    }

    /**
     * 平仓成交时匹配最近一笔未平仓的 InOutOrder，更新盈亏。
     */
    public void recordClose(Order order) {
        Optional<InOutOrder> openOpt = findOpenRecord(order.getStrategyId(), order.getSymbol());
        if (openOpt.isEmpty()) {
            log.warn("未找到对应的开仓记录: strategyId={}, symbol={}", order.getStrategyId(), order.getSymbol());
            return;
        }
        InOutOrder record = openOpt.get();
        record.close(order.getOrderId(),
                order.getAvgFilledPrice(),
                order.getFilledQuantity(),
                LocalDateTime.now());
        inOutOrderMapper.updateById(record);
        log.info("平仓记录: enterOrderId={}, exitOrderId={}, symbol={}, pnl={}",
                record.getEnterOrderId(), order.getOrderId(), order.getSymbol(), record.getRealizedPnl());
    }

    /**
     * 查询策略当前未平仓记录。
     */
    public List<InOutOrder> getOpenRecords(String strategyId) {
        return inOutOrderMapper.selectList(new LambdaQueryWrapper<InOutOrder>()
                .eq(InOutOrder::getStrategyId, strategyId)
                .eq(InOutOrder::isClosed, false)
                .orderByAsc(InOutOrder::getEnterTime));
    }

    /**
     * 查询策略历史所有已平仓记录。
     */
    public List<InOutOrder> getClosedRecords(String strategyId) {
        return inOutOrderMapper.selectList(new LambdaQueryWrapper<InOutOrder>()
                .eq(InOutOrder::getStrategyId, strategyId)
                .eq(InOutOrder::isClosed, true)
                .orderByDesc(InOutOrder::getExitTime));
    }

    /**
     * 计算策略已实现总盈亏。
     */
    public BigDecimal getTotalRealizedPnl(String strategyId) {
        return getClosedRecords(strategyId).stream()
                .map(InOutOrder::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<InOutOrder> findOpenRecord(String strategyId, String symbol) {
        return Optional.ofNullable(inOutOrderMapper.selectOne(new LambdaQueryWrapper<InOutOrder>()
                .eq(strategyId != null, InOutOrder::getStrategyId, strategyId)
                .eq(InOutOrder::getSymbol, symbol)
                .eq(InOutOrder::isClosed, false)
                .orderByAsc(InOutOrder::getEnterTime)
                .last("LIMIT 1")));
    }
}
