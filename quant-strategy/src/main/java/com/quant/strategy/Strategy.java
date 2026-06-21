package com.quant.strategy;

import com.quant.common.enums.Signal;
import com.quant.common.model.Order;
import com.quant.common.model.TickData;

/**
 * 策略接口：定义完整生命周期钩子
 */
public interface Strategy {

    String getStrategyId();

    String getStrategyName();

    /** 策略初始化（注册订阅、重置内部状态） */
    void init();

    /**
     * 每个 Tick/Bar 回调，返回交易信号。
     */
    Signal onTick(TickData tick);

    /**
     * 每个 Tick 后的自定义出场检查。
     * 策略可在此实现追踪止损、条件止盈等逻辑，返回 SELL/BUY 触发平仓，HOLD 则不操作。
     */
    default Signal onCheckExit(TickData tick) {
        return Signal.HOLD;
    }

    /**
     * 订单状态变更通知（对应 banbot OdChgNew/OdChgEnterFill/OdChgExitFill 等事件）。
     * 策略可感知自己下的单是否成交，并据此调整后续逻辑。
     */
    default void onOrderChange(Order order, OrderChangeType changeType) {
        // 默认空实现，子类按需覆盖
    }

    /** 策略停止（清理资源、平掉所有持仓信号） */
    void stop();

    boolean isRunning();

    /** 订单变更类型 */
    enum OrderChangeType {
        NEW,          // 新建订单
        ENTER_FILL,   // 开仓成交
        EXIT_FILL,    // 平仓成交
        CANCELLED,    // 撤单
        REJECTED      // 风控拒绝
    }
}

