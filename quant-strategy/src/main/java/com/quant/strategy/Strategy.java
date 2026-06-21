package com.quant.strategy;

import com.quant.common.enums.Signal;
import com.quant.common.model.TickData;

/**
 * 策略接口
 * 所有交易策略需实现此接口
 */
public interface Strategy {

    /**
     * 获取策略ID
     */
    String getStrategyId();

    /**
     * 获取策略名称
     */
    String getStrategyName();

    /**
     * 初始化策略
     */
    void init();

    /**
     * 接收Tick数据并计算信号
     * @param tick 行情数据
     * @return 交易信号
     */
    Signal onTick(TickData tick);

    /**
     * 停止策略
     */
    void stop();

    /**
     * 策略是否运行中
     */
    boolean isRunning();
}
