package com.quant.market.kline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * K线历史数据批量导入服务。
 * 支持 Binance 公开数据格式（无表头 CSV / ZIP）：
 *   open_time, open, high, low, close, volume, close_time, quote_volume, trade_count, ...
 *
 * 同一月份 CSV 和 ZIP 同时存在时优先取 ZIP，避免重复导入。
 */
@Slf4j
@Service
public class KlineImportService {

    private static final int BATCH_SIZE = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final ThreadPoolExecutor executor;

    public KlineImportService(JdbcTemplate jdbcTemplate,
                              @Qualifier("klineImportExecutor") ThreadPoolExecutor executor) {
        this.jdbcTemplate = jdbcTemplate;
        this.executor = executor;
    }

    /**
     * 按月份多线程导入指定目录下的所有 K 线文件。
     * 同名 CSV/ZIP 只取 ZIP；各月份文件并发执行，互不干扰。
     */
    public ImportResult importDir(String dataDir, String symbol, String interval) {
        String table = "kline_" + symbol.toLowerCase();
        String sql = buildInsertSql(table);

        List<Path> files = collectFiles(dataDir);
        if (files.isEmpty()) {
            return ImportResult.of(0, 0, "目录下无 CSV/ZIP 文件: " + dataDir);
        }

        log.info("开始多线程导入K线: symbol={}, interval={}, 文件数={}, 线程数={}, 目录={}",
                symbol, interval, files.size(), executor.getCorePoolSize(), dataDir);

        AtomicLong totalRows = new AtomicLong(0);
        AtomicInteger totalFiles = new AtomicInteger(0);
        int total = files.size();

        List<CompletableFuture<Void>> futures = new ArrayList<>(files.size());

        for (Path file : files) {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try {
                    long rows = importFile(file, sql, interval);
                    int done = totalFiles.incrementAndGet();
                    totalRows.addAndGet(rows);
                    log.info("  [{}/{}] {} -> +{} 行  (线程:{})",
                            done, total, file.getFileName(), rows, Thread.currentThread().getName());
                } catch (Exception e) {
                    log.error("  导入失败: {}", file.getFileName(), e);
                }
            }, executor);
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        String msg = String.format("完成: %d 个文件，共导入 %d 行 -> %s",
                totalFiles.get(), totalRows.get(), table);
        log.info(msg);
        return ImportResult.of(totalFiles.get(), totalRows.get(), msg);
    }

    /**
     * 去重：同月 CSV + ZIP 只保留 ZIP（优先），按文件名排序后并发执行。
     * 文件名规律: BTCUSDT-1m-2020-01.csv / .zip
     */
    private List<Path> collectFiles(String dataDir) {
        File dir = new File(dataDir);
        if (!dir.isDirectory()) return List.of();

        File[] all = dir.listFiles(f ->
                f.isFile() && (f.getName().endsWith(".csv") || f.getName().endsWith(".zip")));
        if (all == null) return List.of();

        // key = 去掉后缀的文件名（即月份标识），value = 优先 ZIP
        Map<String, Path> deduped = new LinkedHashMap<>();
        for (File f : all) {
            String name = f.getName();
            String key = name.endsWith(".zip")
                    ? name.substring(0, name.length() - 4)
                    : name.substring(0, name.length() - 4);
            Path existing = deduped.get(key);
            if (existing == null || name.endsWith(".zip")) {
                deduped.put(key, Paths.get(f.getAbsolutePath()));
            }
        }

        List<Path> result = new ArrayList<>(deduped.values());
        result.sort(null);
        return result;
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
                    Long.parseLong(cols[0].trim()),
                    interval,
                    cols[1].trim(),
                    cols[2].trim(),
                    cols[3].trim(),
                    cols[4].trim(),
                    cols[5].trim(),
                    Long.parseLong(cols[6].trim()),
                    cols[7].trim(),
                    Integer.parseInt(cols[8].trim()),
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
