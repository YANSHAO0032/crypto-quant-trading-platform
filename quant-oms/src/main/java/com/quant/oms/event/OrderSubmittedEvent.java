package com.quant.oms.event;

import org.springframework.context.ApplicationEvent;

/** 订单提交事件：OrderManager → ExecutionService，解耦OMS与执行层。 */
public class OrderSubmittedEvent extends ApplicationEvent {

    private final String orderId;

    public OrderSubmittedEvent(Object source, String orderId) {
        super(source);
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
}
