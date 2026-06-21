package com.quant.risk;

import com.quant.common.model.Position;
import com.quant.risk.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仓位管理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionManager {

    private final PositionMapper positionMapper;

    /**
     * 获取当前持仓。
     */
    public BigDecimal getPosition(String symbol) {
        Position position = positionMapper.selectById(normalizeSymbol(symbol));
        return position == null ? BigDecimal.ZERO : position.getQuantity();
    }

    /**
     * 更新持仓。
     */
    public void updatePosition(String symbol, BigDecimal delta) {
        String normalizedSymbol = normalizeSymbol(symbol);
        positionMapper.addPosition(normalizedSymbol, delta, LocalDateTime.now());
        BigDecimal updated = getPosition(normalizedSymbol);
        log.info("仓位更新: symbol={}, delta={}, current={}", normalizedSymbol, delta, updated);
    }

    /**
     * 设置持仓。
     */
    public void setPosition(String symbol, BigDecimal position) {
        String normalizedSymbol = normalizeSymbol(symbol);
        Position existing = positionMapper.selectById(normalizedSymbol);
        LocalDateTime now = LocalDateTime.now();
        Position entity = Position.builder()
                .symbol(normalizedSymbol)
                .quantity(position)
                .createTime(existing == null ? now : existing.getCreateTime())
                .updateTime(now)
                .build();

        if (existing == null) {
            positionMapper.insert(entity);
        } else {
            positionMapper.updateById(entity);
        }
        log.info("仓位设置: symbol={}, position={}", normalizedSymbol, position);
    }

    /**
     * 获取所有持仓。
     */
    public Map<String, BigDecimal> getAllPositions() {
        return positionMapper.selectList(null).stream()
                .collect(Collectors.toUnmodifiableMap(Position::getSymbol, Position::getQuantity));
    }

    /**
     * 清空持仓。
     */
    public void clearAll() {
        positionMapper.delete(null);
        log.info("所有仓位已清空");
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.toUpperCase();
    }
}
