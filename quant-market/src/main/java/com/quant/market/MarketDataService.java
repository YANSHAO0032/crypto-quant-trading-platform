package com.quant.market;

import com.quant.common.model.TickData;

import java.util.List;
import java.util.function.Consumer;

/**
 * 行情数据服务接口
 */
public interface MarketDataService {

    /**
     * 订阅实时行情
     * @param symbol 交易对
     * @param callback 行情回调
     */
    void subscribe(String symbol, Consumer<TickData> callback);

    /**
     * 取消订阅
     * @param symbol 交易对
     */
    void unsubscribe(String symbol);

    /**
     * 获取最新Tick
     * @param symbol 交易对
     * @return 最新行情
     */
    TickData getLatestTick(String symbol);

    /**
     * 获取历史K线数据
     * @param symbol 交易对
     * @param interval K线周期 (e.g., "1m", "5m", "1h", "1d")
     * @param limit 数量
     * @return K线数据列表
     */
    List<TickData> getHistoricalKlines(String symbol, String interval, int limit);
}
