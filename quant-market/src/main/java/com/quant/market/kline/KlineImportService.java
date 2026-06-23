package com.quant.market.kline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * K线历史数据批量导入服务。
 * 支持 Binance 公开数据格式（无表头 CSV / ZIP）：
 *   open_time, open, high, low, close, volume, close_time, quote_volume, trade_count, ...
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KlineImportService {

    private static final int BATCH_SIZE = 2000;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 批量导入指定目录下的所有 CSV/ZIP 文件。
     *
     * @param dataDir  文件目录，如 D:\java_workspace\btcdata
     * @param symbol   交易对，如 BTCUSDT（决定写入哪张分表）
     * @param interval K线周期，如 1m
     * @return 导入结果
     */
    public ImportResult importDir(String dataDir, String symbol, String interval) {
        String table = "kline_" + symbol.toLowerCase();
        String sql = buildInsertSql(table);

        List<Path> files = collectFiles(dataDir);
        if (files.isEmpty()) {
            return ImportResult.of(0, 0, "目录下无 CSV/ZIP 文件: " + dataDir);
        }

        log.info("开始导入K线数据: symbol={}, interval={}, 文件数={}, 目录={}", symbol, interval, files.size(), dataDir);

        long totalRows = 0;
        int fileCount = 0;

        for (Path file : files) {
            try {
                long rows = importFile(file, sql, interval);
                totalRows += rows;
                fileCount++;
                log.info("  [{}/{}] {} -> +{} 行", fileCount, files.size(), file.getFileName(), rows);
            } catch (Exception e) {
                log.error("  导入失败: {}", file.getFileName(), e);
            }
        }

        String msg = String.format("完成: %d 个文件，共导入 %d 行 -> %s", fileCount, totalRows, table);
        log.info(msg);
        return ImportResult.of(fileCount, totalRows, msg);
    }

    private long importFile(Path file, String sql, String interval) throws IOException {
        if (file.toString().endsWith(".zip")) {
            return importZip(file, sql, interval);
        }
        return importCsv(file, sql, interval);
    }

    private long importCsv(Path csvPath, String sql, String interval) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            return doBatchInsert(reader, sql, interval);
        }
    }

    private long importZip(Path zipPath, String sql, String interval) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.entries().nextElement();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zipFile.getInputStream(entry)))) {
                return doBatchInsert(reader, sql, interval);
            }
        }
    }

    private long doBatchInsert(BufferedReader reader, String sql, String interval) throws IOException {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            String[] cols = line.split(",");
            if (cols.length < 9) continue;

            batch.add(new Object[]{
                    Long.parseLong(cols[0].trim()),    // open_time
                    interval,                           // interval
                    cols[1].trim(),                    // open_price
                    cols[2].trim(),                    // high_price
                    cols[3].trim(),                    // low_price
                    cols[4].trim(),                    // close_price
                    cols[5].trim(),                    // volume
                    Long.parseLong(cols[6].trim()),    // close_time
                    cols[7].trim(),                    // quote_volume
                    Integer.parseInt(cols[8].trim()),  // trade_count
            });

            if (batch.size() >= BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, batch);
                total += batch.size();
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
            total += batch.size();
        }

        return total;
    }

    private List<Path> collectFiles(String dataDir) {
        File dir = new File(dataDir);
        if (!dir.isDirectory()) return List.of();

        List<Path> result = new ArrayList<>();
        File[] files = dir.listFiles(f ->
                f.isFile() && (f.getName().endsWith(".csv") || f.getName().endsWith(".zip")));
        if (files != null) {
            for (File f : files) {
                result.add(Paths.get(f.getAbsolutePath()));
            }
            result.sort(null);
        }
        return result;
    }

    private String buildInsertSql(String table) {
        return String.format("""
                INSERT IGNORE INTO %s
                    (open_time, `interval`, open_price, high_price, low_price, close_price,
                     volume, close_time, quote_volume, trade_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, table);
    }

    public record ImportResult(int fileCount, long rowCount, String message) {
        static ImportResult of(int fileCount, long rowCount, String message) {
            return new ImportResult(fileCount, rowCount, message);
        }
    }
}
