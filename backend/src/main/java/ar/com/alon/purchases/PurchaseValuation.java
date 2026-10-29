package ar.com.alon.purchases;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Allocates every cent once, with deterministic remainder on the last line. */
public final class PurchaseValuation {
    private PurchaseValuation() {}

    public record Line(long articleId, BigDecimal quantity, BigDecimal value) {}

    public record ValuedLine(long articleId, BigDecimal quantity, BigDecimal value) {}

    public static List<ValuedLine> allocate(List<Line> lines, BigDecimal extras) {
        BigDecimal subtotal =
                lines.stream().map(Line::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalQuantity =
                lines.stream().map(Line::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalQuantity.signum() <= 0)
            throw new IllegalArgumentException("La compra debe contener cantidades positivas.");
        BigDecimal allocated = BigDecimal.ZERO;
        List<ValuedLine> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            BigDecimal share =
                    index == lines.size() - 1
                            ? extras.subtract(allocated)
                            : extras.multiply(
                                            subtotal.signum() == 0 ? line.quantity() : line.value())
                                    .divide(
                                            subtotal.signum() == 0 ? totalQuantity : subtotal,
                                            2,
                                            RoundingMode.DOWN);
            allocated = allocated.add(share);
            result.add(new ValuedLine(line.articleId(), line.quantity(), line.value().add(share)));
        }
        return List.copyOf(result);
    }

    public static BigDecimal revisedCost(
            BigDecimal stock,
            BigDecimal cost,
            BigDecimal oldQuantity,
            BigDecimal oldValue,
            BigDecimal newQuantity,
            BigDecimal newValue) {
        BigDecimal newStock = stock.subtract(oldQuantity).add(newQuantity);
        if (stock.compareTo(oldQuantity) < 0 || newStock.signum() < 0) {
            throw new IllegalArgumentException(
                    "La compra ya tuvo salidas; registrá una corrección de stock en lugar de editarla.");
        }
        BigDecimal value = stock.multiply(cost).subtract(oldValue).add(newValue);
        if (value.signum() < 0)
            throw new IllegalArgumentException(
                    "No se puede reconstruir el costo. Revisá los movimientos de este artículo.");
        return newStock.signum() == 0 ? cost : value.divide(newStock, 2, RoundingMode.HALF_UP);
    }
}
