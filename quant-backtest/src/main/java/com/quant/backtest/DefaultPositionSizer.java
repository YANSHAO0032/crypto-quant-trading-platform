package com.quant.backtest;

import com.quant.common.enums.Signal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DefaultPositionSizer implements PositionSizer {

    private static final int QTY_SCALE = 8;
    private static final RoundingMode RM = RoundingMode.DOWN;

    @Override
    public BigDecimal size(Signal signal, BacktestAccount account, BigDecimal price, BacktestConfig config) {
        if (signal == Signal.HOLD) {
            throw new IllegalArgumentException("HOLD signal cannot be sized");
        }
        requirePositive(price, "price");

        BigDecimal positionQty = account.getPositionQty();
        BigDecimal signedSignalQty = signal == Signal.BUY ? BigDecimal.ONE : BigDecimal.ONE.negate();
        if (positionQty.compareTo(BigDecimal.ZERO) != 0 && positionQty.signum() != signedSignalQty.signum()) {
            return scaleQty(positionQty.abs());
        }

        PositionSizingMode mode = config.getSizingMode() == null ? PositionSizingMode.FIXED_QTY : config.getSizingMode();
        return switch (mode) {
            case FIXED_QTY -> scaleQty(requirePositive(config.getOrderQuantity(), "orderQuantity"));
            case FIXED_NOTIONAL -> scaleQty(requirePositive(config.getOrderNotional(), "orderNotional")
                    .divide(price, QTY_SCALE, RM));
            case EQUITY_PERCENT -> scaleQty(currentEquity(account, price)
                    .multiply(requirePositive(config.getEquityPercent(), "equityPercent"))
                    .divide(price, QTY_SCALE, RM));
        };
    }

    private BigDecimal currentEquity(BacktestAccount account, BigDecimal price) {
        return account.getCash().add(account.getPositionQty().multiply(price));
    }

    private BigDecimal requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private BigDecimal scaleQty(BigDecimal quantity) {
        return quantity.setScale(QTY_SCALE, RM);
    }
}
