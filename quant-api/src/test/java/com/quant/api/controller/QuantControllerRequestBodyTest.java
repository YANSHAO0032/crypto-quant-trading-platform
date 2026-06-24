package com.quant.api.controller;

import com.quant.backtest.BacktestConfig;
import com.quant.backtest.BacktestEngine;
import com.quant.backtest.PositionSizingMode;
import com.quant.common.enums.OrderSide;
import com.quant.common.enums.OrderStatus;
import com.quant.common.enums.OrderType;
import com.quant.common.model.BacktestReport;
import com.quant.common.model.Order;
import com.quant.oms.IOrderManager;
import com.quant.oms.InOutOrderService;
import com.quant.risk.PositionManager;
import com.quant.risk.WalletManager;
import com.quant.strategy.Strategy;
import com.quant.strategy.StrategyRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuantControllerRequestBodyTest {

    private IOrderManager orderManager;
    private BacktestEngine backtestEngine;
    private StrategyRunner strategyRunner;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderManager = mock(IOrderManager.class);
        backtestEngine = mock(BacktestEngine.class);
        strategyRunner = mock(StrategyRunner.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new QuantController(
                orderManager,
                backtestEngine,
                strategyRunner,
                mock(PositionManager.class),
                mock(WalletManager.class),
                mock(InOutOrderService.class)
        )).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void createOrderAcceptsRequestBodyDto() throws Exception {
        when(orderManager.createOrder(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId("order-1");
            order.setStatus(OrderStatus.PENDING);
            return order;
        });

        mockMvc.perform(post("/api/quant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol":"BTCUSDT",
                                  "side":"BUY",
                                  "type":"LIMIT",
                                  "price":100,
                                  "quantity":2,
                                  "strategyId":"S1"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderManager).createOrder(captor.capture());
        Order order = captor.getValue();
        assertEquals("BTCUSDT", order.getSymbol());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderType.LIMIT, order.getType());
        assertEquals(new BigDecimal("100"), order.getPrice());
        assertEquals(new BigDecimal("2"), order.getQuantity());
        assertEquals("S1", order.getStrategyId());
    }

    @Test
    void createOrderIgnoresClientManagedFields() throws Exception {
        when(orderManager.createOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/quant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId":"client-order",
                                  "status":"FILLED",
                                  "symbol":"BTCUSDT",
                                  "side":"BUY",
                                  "type":"LIMIT",
                                  "price":100,
                                  "quantity":2
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderManager).createOrder(captor.capture());
        Order order = captor.getValue();
        assertEquals(null, order.getOrderId());
        assertEquals(null, order.getStatus());
    }

    @Test
    void startStrategyAcceptsRequestBodyDto() throws Exception {
        Strategy strategy = mock(Strategy.class);
        when(strategyRunner.findById("MA_CROSS")).thenReturn(Optional.of(strategy));
        when(strategyRunner.isRunning("MA_CROSS")).thenReturn(false);

        mockMvc.perform(post("/api/quant/strategies/MA_CROSS/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"ETHUSDT"}
                                """))
                .andExpect(status().isOk());

        verify(strategyRunner).start("MA_CROSS", "ETHUSDT");
    }

    @Test
    void klineBacktestAcceptsRequestBodyDto() throws Exception {
        Strategy strategy = mock(Strategy.class);
        BacktestReport report = BacktestReport.builder().backtestId("bt-1").build();
        when(strategyRunner.findById("MA_CROSS")).thenReturn(Optional.of(strategy));
        when(strategyRunner.isRunning("MA_CROSS")).thenReturn(false);
        when(backtestEngine.runKlineBacktest(eq(strategy), eq("BTCUSDT"), eq("1m"),
                eq(1_767_196_800_000L), eq(1_782_230_400_000L), eq(new BigDecimal("5000")),
                any(BacktestConfig.class)))
                .thenReturn(report);

        mockMvc.perform(post("/api/quant/backtest/MA_CROSS/kline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol":"BTCUSDT",
                                  "interval":"1m",
                                  "startMs":"1767196800000",
                                  "endMs":"1782230400000",
                                  "capital":5000,
                                  "sizingMode":"EQUITY_PERCENT",
                                  "equityPercent":0.2,
                                  "allowPartialData":false,
                                  "timezone":"Asia/Shanghai"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<BacktestConfig> configCaptor = ArgumentCaptor.forClass(BacktestConfig.class);
        verify(backtestEngine).runKlineBacktest(eq(strategy), eq("BTCUSDT"), eq("1m"),
                eq(1_767_196_800_000L), eq(1_782_230_400_000L), eq(new BigDecimal("5000")),
                configCaptor.capture());
        BacktestConfig config = configCaptor.getValue();
        assertEquals(PositionSizingMode.EQUITY_PERCENT, config.getSizingMode());
        assertEquals(new BigDecimal("0.2"), config.getEquityPercent());
        assertEquals(false, config.isAllowPartialData());
        assertEquals("Asia/Shanghai", config.getTimezone());
    }

    @Test
    void latestKlineBacktestAcceptsRequestBodyDto() throws Exception {
        Strategy strategy = mock(Strategy.class);
        BacktestReport report = BacktestReport.builder().backtestId("bt-2").build();
        when(strategyRunner.findById("MA_CROSS")).thenReturn(Optional.of(strategy));
        when(strategyRunner.isRunning("MA_CROSS")).thenReturn(false);
        when(backtestEngine.quickKlineBacktest(strategy, "ETHUSDT", "5m", 300, new BigDecimal("200000")))
                .thenReturn(report);

        mockMvc.perform(post("/api/quant/backtest/MA_CROSS/kline/latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol":"ETHUSDT",
                                  "interval":"5m",
                                  "limit":300,
                                  "capital":200000
                                }
                                """))
                .andExpect(status().isOk());

        verify(backtestEngine).quickKlineBacktest(strategy, "ETHUSDT", "5m", 300, new BigDecimal("200000"));
    }

    @Test
    void klineBacktestReturnsBadRequestForInvalidBacktestRange() throws Exception {
        Strategy strategy = mock(Strategy.class);
        when(strategyRunner.findById("PRO_RSI_MR")).thenReturn(Optional.of(strategy));
        when(strategyRunner.isRunning("PRO_RSI_MR")).thenReturn(false);
        when(backtestEngine.runKlineBacktest(eq(strategy), eq("BTCUSDT"), eq("1m"),
                eq(1_767_196_800_000L), eq(1_782_230_400_000L), eq(new BigDecimal("5000")),
                any(BacktestConfig.class)))
                .thenThrow(new IllegalArgumentException("kline coverage incomplete"));

        mockMvc.perform(post("/api/quant/backtest/PRO_RSI_MR/kline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol":"BTCUSDT",
                                  "interval":"1m",
                                  "startMs":"1767196800000",
                                  "endMs":"1782230400000",
                                  "capital":5000
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quickBacktestAcceptsRequestBodyDto() throws Exception {
        Strategy strategy = mock(Strategy.class);
        BacktestReport report = BacktestReport.builder().backtestId("bt-3").build();
        when(strategyRunner.findById("GRID")).thenReturn(Optional.of(strategy));
        when(strategyRunner.isRunning("GRID")).thenReturn(false);
        when(backtestEngine.quickBacktest(strategy, "BTCUSDT", 2000,
                new BigDecimal("65000"), new BigDecimal("100000")))
                .thenReturn(report);

        mockMvc.perform(post("/api/quant/backtest/GRID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol":"BTCUSDT",
                                  "dataCount":2000,
                                  "startPrice":65000,
                                  "capital":100000
                                }
                                """))
                .andExpect(status().isOk());

        verify(backtestEngine).quickBacktest(strategy, "BTCUSDT", 2000,
                new BigDecimal("65000"), new BigDecimal("100000"));
    }
}
