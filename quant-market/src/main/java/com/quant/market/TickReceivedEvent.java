package com.quant.market;

import com.quant.common.model.TickData;
import org.springframework.context.ApplicationEvent;

/** 行情 Tick 到达事件，用于解耦 MarketDataService 与上层监控组件 */
public class TickReceivedEvent extends ApplicationEvent {

    private final TickData tick;

    public TickReceivedEvent(Object source, TickData tick) {
        super(source);
        this.tick = tick;
    }

    public TickData getTick() {
        return tick;
    }
}
