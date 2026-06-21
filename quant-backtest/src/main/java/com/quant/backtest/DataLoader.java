package com.quant.backtest;

import com.quant.common.model.TickData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史数据加载器
 * 从CSV文件加载历史行情数据用于回测
 */
@Slf4j
@Component
public class DataLoader {

    /**
     * 从CSV文件加载K线数据
     * 格式: timestamp,open,high,low,close,volume
     * @param filePath CSV文件路径
     * @param symbol 交易对
     * @return TickData列表
     */
    public List<TickData> loadFromCsv(String filePath, String symbol) {
        List<TickData> dataList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length < 6) continue;

                TickData tick = TickData.builder()
                        .symbol(symbol)
                        .interval("TICK")
                        .timestamp(Long.parseLong(parts[0].trim()))
                        .openPrice(new BigDecimal(parts[1].trim()))
                        .highPrice(new BigDecimal(parts[2].trim()))
                        .lowPrice(new BigDecimal(parts[3].trim()))
                        .lastPrice(new BigDecimal(parts[4].trim()))
                        .volume(new BigDecimal(parts[5].trim()))
                        .build();

                dataList.add(tick);
            }

            log.info("数据加载完成: file={}, records={}", filePath, dataList.size());
        } catch (IOException e) {
            log.error("数据加载失败: {}", filePath, e);
        }

        return dataList;
    }

    /**
     * 生成模拟数据（用于测试）
     */
    public List<TickData> generateMockData(String symbol, int count, BigDecimal startPrice) {
        List<TickData> dataList = new ArrayList<>();
        BigDecimal price = startPrice;

        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.48) * 100; // 轻微上涨偏向
            price = price.add(BigDecimal.valueOf(change));
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                price = startPrice;
            }

            dataList.add(TickData.builder()
                    .symbol(symbol)
                    .interval("TICK")
                    .lastPrice(price)
                    .bidPrice(price.subtract(BigDecimal.ONE))
                    .askPrice(price.add(BigDecimal.ONE))
                    .volume(BigDecimal.valueOf(Math.random() * 1000))
                    .timestamp(System.currentTimeMillis() + i * 60000L)
                    .build());
        }

        return dataList;
    }
}
