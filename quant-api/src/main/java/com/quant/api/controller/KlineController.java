package com.quant.api.controller;

import com.quant.market.kline.KlineImportService;
import com.quant.market.kline.KlineImportService.ImportResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kline")
@RequiredArgsConstructor
public class KlineController {

    private final KlineImportService klineImportService;

    /**
     * 批量导入 K 线历史数据。
     * 示例:
     *   POST /api/kline/import
     *   {"dataDir": "D:\\java_workspace\\btcdata", "symbol": "BTCUSDT", "interval": "1m"}
     */
    @PostMapping("/import")
    public ImportResult importKlines(@RequestBody ImportRequest req) {
        return klineImportService.importDir(req.getDataDir(), req.getSymbol(), req.getInterval());
    }

    @Data
    public static class ImportRequest {
        private String dataDir;
        private String symbol = "BTCUSDT";
        private String interval = "1m";
    }
}

