package com.quant.oms;

import com.quant.strategy.SignalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 策略信号 → 下单处理器。
 * 监听 StrategyRunner 发布的 SignalEvent，调用 OrderService 下单。
 * 位于 quant-oms 模块，实现策略模块与OMS解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalOrderHandler {

    private final OrderService orderService;

    @EventListener
    public void onSignal(SignalEvent event) {
        log.info("收到策略信号: strategyId={}, symbol={}, signal={}, price={}",
                event.getStrategyId(), event.getSymbol(), event.getSignal(), event.getPrice());
        orderService.placeOrder(
                event.getSignal(),
                event.getSymbol(),
                event.getPrice(),
                event.getQuantity(),
                event.getStrategyId()
        );
    }
}
