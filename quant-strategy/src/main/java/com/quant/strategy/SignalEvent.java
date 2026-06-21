package com.quant.strategy;

import com.quant.common.enums.Signal;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/** 策略信号事件：StrategyRunner → SignalOrderHandler，解耦策略模块与OMS。 */
public class SignalEvent extends ApplicationEvent {

    private final String strategyId;
    private final String symbol;
    private final Signal signal;
    private final BigDecimal price;
    private final BigDecimal quantity;

    public SignalEvent(Object source, String strategyId, String symbol,
                       Signal signal, BigDecimal price, BigDecimal quantity) {
        super(source);
        this.strategyId = strategyId;
        this.symbol = symbol;
        this.signal = signal;
        this.price = price;
        this.quantity = quantity;
    }

    public String getStrategyId() { return strategyId; }
    public String getSymbol() { return symbol; }
    public Signal getSignal() { return signal; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
}
