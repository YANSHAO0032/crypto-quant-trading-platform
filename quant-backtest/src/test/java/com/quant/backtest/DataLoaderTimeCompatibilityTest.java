package com.quant.backtest;

import com.quant.common.model.Kline;
import com.quant.market.MarketDataService;
import com.quant.market.mapper.KlineMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataLoaderTimeCompatibilityTest {

    @Test
    void loadLatestKlinesNormalizesMixedTimestampUnitsAndSortsChronologically() {
        KlineMapper mapper = mock(KlineMapper.class);
        when(mapper.selectLatest("1m", 3)).thenReturn(List.of(
                kline(1_780_236_420_000_000L, 1_780_236_479_999_000L, "102"),
                kline(1_780_236_300_000L, 1_780_236_359_999L, "100"),
                kline(1_780_236_360_000_000L, 1_780_236_419_999_000L, "101")
        ));

        DataLoader loader = new DataLoader(mock(MarketDataService.class), mapper);
        List<Kline> klines = loader.loadLatestKlines("BTCUSDT", "1m", 3);

        assertEquals(List.of(1_780_236_300_000L, 1_780_236_360_000L, 1_780_236_420_000L),
                klines.stream().map(Kline::getOpenTime).toList());
        assertEquals(List.of(new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("102")),
                klines.stream().map(Kline::getClosePrice).toList());
    }

    private Kline kline(long openTime, long closeTime, String closePrice) {
        return Kline.builder()
                .openTime(openTime)
                .closeTime(closeTime)
                .openPrice(new BigDecimal(closePrice))
                .highPrice(new BigDecimal(closePrice))
                .lowPrice(new BigDecimal(closePrice))
                .closePrice(new BigDecimal(closePrice))
                .volume(BigDecimal.ONE)
                .quoteVolume(BigDecimal.ONE)
                .tradeCount(1)
                .build();
    }
}
