package com.quant.backtest;

import com.quant.common.enums.Signal;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Getter
public class BacktestAccount {

    private static final int SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final BigDecimal initialCapital;
    private final BigDecimal feeRate;
    private final List<BacktestFill> fills = new ArrayList<>();
    private final List<BacktestClosedTrade> closedTrades = new ArrayList<>();
    private final List<EquityPoint> equityCurve = new ArrayList<>();

    private BigDecimal cash;
    private BigDecimal positionQty = scaled(BigDecimal.ZERO);
    private BigDecimal avgEntryPrice = scaled(BigDecimal.ZERO);
    private BigDecimal positionEntryFee = scaled(BigDecimal.ZERO);
    private BigDecimal realizedPnl = scaled(BigDecimal.ZERO);
    private BigDecimal totalFee = scaled(BigDecimal.ZERO);
    private Long entryTimestampMs;

    public BacktestAccount(BigDecimal initialCapital, BigDecimal feeRate) {
        this.initialCapital = scaled(initialCapital);
        this.cash = scaled(initialCapital);
        this.feeRate = feeRate == null ? BigDecimal.ZERO : feeRate;
    }

    public BacktestFill execute(Signal signal, BigDecimal price, BigDecimal quantity, long timestampMs) {
        if (signal == Signal.HOLD) {
            throw new IllegalArgumentException("HOLD signal cannot create a fill");
        }

        BigDecimal fillPrice = scaled(price);
        BigDecimal fillQty = scaled(quantity);
        if (fillQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        BigDecimal signedQty = signal == Signal.BUY ? fillQty : fillQty.negate();
        BigDecimal notional = scaled(fillPrice.multiply(fillQty));
        BigDecimal fee = scaled(notional.multiply(feeRate));
        if (signal == Signal.BUY && cash.compareTo(notional.add(fee)) < 0) {
            throw new IllegalArgumentException("insufficient cash for buy order");
        }

        cash = signal == Signal.BUY
                ? scaled(cash.subtract(notional).subtract(fee))
                : scaled(cash.add(notional).subtract(fee));
        totalFee = scaled(totalFee.add(fee));

        updatePosition(signal, signedQty, fillPrice, fillQty, fee, timestampMs);

        BacktestFill fill = BacktestFill.builder()
                .signal(signal)
                .price(fillPrice)
                .quantity(fillQty)
                .notional(notional)
                .fee(fee)
                .timestampMs(timestampMs)
                .cashAfter(cash)
                .positionQtyAfter(positionQty)
                .build();
        fills.add(fill);
        return fill;
    }

    public EquityPoint markInitialEquity(long timestampMs) {
        EquityPoint point = EquityPoint.builder()
                .timestampMs(timestampMs)
                .equity(initialCapital)
                .cash(cash)
                .positionQty(positionQty)
                .markPrice(scaled(BigDecimal.ZERO))
                .realizedPnl(realizedPnl)
                .unrealizedPnl(scaled(BigDecimal.ZERO))
                .totalFee(totalFee)
                .build();
        equityCurve.add(point);
        return point;
    }

    public EquityPoint markToMarket(BigDecimal markPrice, long timestampMs) {
        BigDecimal mark = scaled(markPrice);
        BigDecimal positionValue = scaled(positionQty.multiply(mark));
        BigDecimal equity = scaled(cash.add(positionValue));
        BigDecimal unrealized = calculateUnrealized(mark);

        EquityPoint point = EquityPoint.builder()
                .timestampMs(timestampMs)
                .equity(equity)
                .cash(cash)
                .positionQty(positionQty)
                .markPrice(mark)
                .realizedPnl(realizedPnl)
                .unrealizedPnl(unrealized)
                .totalFee(totalFee)
                .build();
        equityCurve.add(point);
        return point;
    }

    public BacktestRunResult toResult() {
        return new BacktestRunResult(initialCapital, List.copyOf(fills), List.copyOf(closedTrades), List.copyOf(equityCurve));
    }

    private void updatePosition(Signal signal, BigDecimal signedQty, BigDecimal price,
                                BigDecimal fillQty, BigDecimal fee, long timestampMs) {
        if (positionQty.compareTo(BigDecimal.ZERO) == 0) {
            positionQty = signedQty;
            avgEntryPrice = price;
            positionEntryFee = fee;
            entryTimestampMs = timestampMs;
            return;
        }

        boolean sameDirection = positionQty.signum() == signedQty.signum();
        BigDecimal oldAbsQty = positionQty.abs();

        if (sameDirection) {
            BigDecimal newAbsQty = oldAbsQty.add(fillQty);
            avgEntryPrice = scaled(avgEntryPrice.multiply(oldAbsQty)
                    .add(price.multiply(fillQty))
                    .divide(newAbsQty, SCALE, RM));
            positionEntryFee = scaled(positionEntryFee.add(fee));
            positionQty = scaled(positionQty.add(signedQty));
            return;
        }

        BigDecimal closedQty = oldAbsQty.min(fillQty);
        BigDecimal closeRatio = fillQty.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : closedQty.divide(fillQty, SCALE, RM);
        BigDecimal entryFeeForClosed = scaled(positionEntryFee.multiply(closedQty).divide(oldAbsQty, SCALE, RM));
        BigDecimal closeFeeForClosed = scaled(fee.multiply(closeRatio));
        BigDecimal grossPnl = calculateGrossPnl(price, closedQty);
        BigDecimal netPnl = scaled(grossPnl.subtract(entryFeeForClosed).subtract(closeFeeForClosed));

        realizedPnl = scaled(realizedPnl.add(netPnl));
        closedTrades.add(BacktestClosedTrade.builder()
                .entryPrice(avgEntryPrice)
                .exitPrice(price)
                .quantity(scaled(closedQty))
                .grossPnl(scaled(grossPnl))
                .fee(scaled(entryFeeForClosed.add(closeFeeForClosed)))
                .netPnl(netPnl)
                .entryTimestampMs(entryTimestampMs)
                .exitTimestampMs(timestampMs)
                .build());

        BigDecimal remainingOldQty = oldAbsQty.subtract(closedQty);
        BigDecimal remainingFillQty = fillQty.subtract(closedQty);

        if (remainingOldQty.compareTo(BigDecimal.ZERO) > 0) {
            positionQty = scaled(BigDecimal.valueOf(positionQty.signum()).multiply(remainingOldQty));
            positionEntryFee = scaled(positionEntryFee.subtract(entryFeeForClosed));
            return;
        }

        if (remainingFillQty.compareTo(BigDecimal.ZERO) > 0) {
            positionQty = scaled(BigDecimal.valueOf(signedQty.signum()).multiply(remainingFillQty));
            avgEntryPrice = price;
            positionEntryFee = scaled(fee.subtract(closeFeeForClosed));
            entryTimestampMs = timestampMs;
            return;
        }

        positionQty = scaled(BigDecimal.ZERO);
        avgEntryPrice = scaled(BigDecimal.ZERO);
        positionEntryFee = scaled(BigDecimal.ZERO);
        entryTimestampMs = null;
    }

    private BigDecimal calculateGrossPnl(BigDecimal exitPrice, BigDecimal closedQty) {
        BigDecimal priceDiff = positionQty.signum() > 0
                ? exitPrice.subtract(avgEntryPrice)
                : avgEntryPrice.subtract(exitPrice);
        return scaled(priceDiff.multiply(closedQty));
    }

    private BigDecimal calculateUnrealized(BigDecimal markPrice) {
        if (positionQty.compareTo(BigDecimal.ZERO) == 0) {
            return scaled(BigDecimal.ZERO);
        }
        BigDecimal diff = positionQty.signum() > 0
                ? markPrice.subtract(avgEntryPrice)
                : avgEntryPrice.subtract(markPrice);
        return scaled(diff.multiply(positionQty.abs()));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCALE, RM);
    }
}
