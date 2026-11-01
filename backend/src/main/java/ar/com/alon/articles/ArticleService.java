// Crea y modifica artículos; valida sus datos y registra los ajustes de stock.
package ar.com.alon.articles;

import static ar.com.alon.articles.ArticlePricing.calculateSalePrice;

import ar.com.alon.articles.dto.ArticleInput;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.shared.validation.DecimalRules;
import ar.com.alon.stock.StockRepository;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService extends StoreJdbcSupport {
    private final StockRepository stock;

    /**
     * Recibe y conserva las dependencias necesarias para las reglas de artículos, precios y stock.
     */
    public ArticleService(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            Clock clock,
            AuditLog audit,
            StockRepository stock) {
        super(jdbc, identity, clock, audit);
        this.stock = stock;
    }

    /**
     * Valida código, nombre, rubro, proveedor y precios; crea el artículo y registra su stock
     * inicial.
     */
    @Transactional
    public Map<String, Object> createArticle(ArticleInput input) {
        if (!identity.permits("compras")) identity.require("costos");
        validateApparel(input);
        String code = required(input.code(), "El código del artículo es obligatorio.");
        String name = required(input.name(), "El nombre del artículo es obligatorio.");
        Integer duplicateName =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM articulos WHERE comercio_id=? AND LOWER(TRIM(nombre))=LOWER(TRIM(?))",
                        Integer.class,
                        identity.storeId(),
                        name);
        if (duplicateName != null && duplicateName > 0)
            throw new IllegalArgumentException(
                    "Ya existe un artículo con ese nombre. Buscalo en el listado de artículos.");
        long categoryId = categoryFor(input, null);
        BigDecimal stock =
                DecimalRules.quantity(
                        input.initialStock(), false, "El stock inicial no puede ser negativo.");
        BigDecimal cost = nonNegative(input.cost(), "El costo no puede ser negativo.");
        BigDecimal freight = BigDecimal.ZERO;
        BigDecimal freightPercent =
                nonNegative(
                        input.freightPercent() == null ? BigDecimal.ZERO : input.freightPercent(),
                        "El porcentaje de flete no puede ser negativo.");
        if (input.freightByValue()
                || (input.freightPerUnit() != null && input.freightPerUnit().signum() != 0))
            throw new IllegalArgumentException(
                    "El flete se carga únicamente como porcentaje del costo.");
        BigDecimal margin =
                nonNegative(
                        input.marginPercent() == null ? BigDecimal.ZERO : input.marginPercent(),
                        "El margen no puede ser negativo.");
        boolean roundPrice = input.roundPrice();
        BigDecimal salePrice = calculateSalePrice(cost, freightPercent, margin, roundPrice);
        long id =
                insert(
                        "INSERT INTO articulos (comercio_id,codigo,nombre,rubro_id,proveedor_principal_id,unidad,costo,flete_unitario,flete_porcentaje,margen_porcentaje,redondeo_precio,cargar_web,precio_venta,stock,stock_minimo) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        identity.storeId(),
                        code,
                        name,
                        categoryId,
                        null,
                        value(input.unit()).isBlank() ? "UNIDAD" : input.unit().trim(),
                        cost,
                        freight,
                        freightPercent,
                        margin,
                        roundPrice,
                        input.publishOnWeb(),
                        salePrice,
                        stock,
                        DecimalRules.quantity(
                                input.minimumStock(),
                                false,
                                "El stock mínimo no puede ser negativo."));
        saveApparel(id, input);
        if (stock.signum() > 0)
            this.stock.addStockMovement(
                    id, "ALTA", stock, "ALTA-" + code, "Stock inicial del artículo");
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    /**
     * Bloquea el artículo del comercio, valida sus nuevos datos y actualiza precios y stock dejando
     * trazabilidad de los ajustes.
     */
    @Transactional
    public Map<String, Object> updateArticle(long id, ArticleInput input) {
        if (!identity.permits("compras")) identity.require("costos");
        Map<String, Object> current =
                jdbc.queryForMap(
                        "SELECT * FROM articulos WHERE id=? AND comercio_id=? FOR UPDATE",
                        id,
                        identity.storeId());
        if (input.apparel() == null
                && current.get("tipo_producto") != null
                && !"ARTICULO".equals(current.get("tipo_producto")))
            throw new IllegalArgumentException(
                    "Completá los datos de indumentaria o calzado para modificar esta variante.");
        validateApparel(input);
        String code = required(input.code(), "El código del artículo es obligatorio.");
        String name = required(input.name(), "El nombre del artículo es obligatorio.");
        Integer duplicateName =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM articulos WHERE comercio_id=? AND LOWER(TRIM(nombre))=LOWER(TRIM(?)) AND id<>?",
                        Integer.class,
                        identity.storeId(),
                        name,
                        id);
        if (duplicateName != null && duplicateName > 0)
            throw new IllegalArgumentException("Ya existe otro artículo con ese nombre.");
        long categoryId = categoryFor(input, current);
        Long previousSupplier =
                current.get("proveedor_principal_id") instanceof Number supplier
                        ? supplier.longValue()
                        : null;
        Long supplierId = input.supplierId() > 0 ? input.supplierId() : previousSupplier;
        if (input.supplierId() > 0)
            assertBelongs(
                    "proveedores",
                    input.supplierId(),
                    java.util.Objects.equals(previousSupplier, supplierId));
        BigDecimal cost = nonNegative(input.cost(), "El costo no puede ser negativo.");
        BigDecimal freight = BigDecimal.ZERO;
        BigDecimal freightPercent =
                nonNegative(
                        input.freightPercent() == null ? BigDecimal.ZERO : input.freightPercent(),
                        "El porcentaje de flete no puede ser negativo.");
        if (input.freightByValue()
                || (input.freightPerUnit() != null && input.freightPerUnit().signum() != 0))
            throw new IllegalArgumentException(
                    "El flete se carga únicamente como porcentaje del costo.");
        BigDecimal margin =
                nonNegative(
                        input.marginPercent() == null ? BigDecimal.ZERO : input.marginPercent(),
                        "El margen no puede ser negativo.");
        BigDecimal price =
                input.salePrice() == null
                        ? calculateSalePrice(cost, freightPercent, margin, input.roundPrice())
                        : nonNegative(input.salePrice(), "El precio no puede ser negativo.");
        // Un cambio manual de cantidad debe quedar registrado como ajuste de stock.
        BigDecimal oldStock = new BigDecimal(current.get("stock").toString());
        BigDecimal requestedStock = input.initialStock() == null ? oldStock : input.initialStock();
        if (requestedStock.signum() < 0)
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        BigDecimal newStock =
                DecimalRules.quantity(requestedStock, false, "La cantidad no puede ser negativa.");
        jdbc.update(
                "UPDATE articulos SET codigo=?,nombre=?,rubro_id=?,proveedor_principal_id=?,unidad=?,costo=?,flete_unitario=?,flete_porcentaje=?,margen_porcentaje=?,redondeo_precio=?,cargar_web=?,precio_venta=?,stock_minimo=? WHERE id=? AND comercio_id=?",
                code,
                name,
                categoryId,
                supplierId,
                value(input.unit()).isBlank() ? "UNIDAD" : input.unit().trim(),
                cost,
                freight,
                freightPercent,
                margin,
                input.roundPrice(),
                input.publishOnWeb(),
                price,
                DecimalRules.quantity(
                        input.minimumStock(), false, "El stock mínimo no puede ser negativo."),
                id,
                identity.storeId());
        jdbc.update(
                "UPDATE articulos SET stock=? WHERE id=? AND comercio_id=?",
                newStock,
                id,
                identity.storeId());
        if (oldStock.compareTo(newStock) != 0)
            this.stock.addStockMovement(
                    id,
                    "AJUSTE",
                    newStock.subtract(oldStock),
                    "AJUSTE-" + id,
                    "Cantidad ajustada de "
                            + oldStock.toPlainString()
                            + " a "
                            + newStock.toPlainString());
        saveApparel(id, input);
        audit.record(
                "articulo",
                id,
                "MODIFICACION",
                current,
                jdbc.queryForMap(
                        "SELECT * FROM articulos WHERE id=? AND comercio_id=?",
                        id,
                        identity.storeId()));
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    /** La clasificación interna no requiere un rubro en el formulario de indumentaria. */
    private long categoryFor(ArticleInput input, Map<String, Object> current) {
        if (input.apparel() == null) {
            assertBelongs("rubros", input.categoryId());
            return input.categoryId();
        }
        if (current != null) return ((Number) current.get("rubro_id")).longValue();
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        var existing =
                jdbc.queryForList(
                        "SELECT id FROM rubros WHERE comercio_id=? AND nombre=? ORDER BY id LIMIT 1",
                        identity.storeId(),
                        "Indumentaria y calzado");
        if (!existing.isEmpty()) return ((Number) existing.getFirst().get("id")).longValue();
        return insert(
                "INSERT INTO rubros (comercio_id,codigo,nombre) VALUES (?,?,?)",
                identity.storeId(),
                "RUB-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Indumentaria y calzado");
    }

    private void validateApparel(ArticleInput input) {
        if (input.apparel() == null) return;
        if (!"UNIDAD".equalsIgnoreCase(value(input.unit())))
            throw new IllegalArgumentException(
                    "Indumentaria y calzado se cargan por unidad o par vendido como unidad.");
        for (BigDecimal quantity : new BigDecimal[] {input.initialStock(), input.minimumStock()})
            if (quantity != null && quantity.stripTrailingZeros().scale() > 0)
                throw new IllegalArgumentException(
                        "Los talles y colores requieren cantidades enteras.");
    }

    private void saveApparel(long id, ArticleInput input) {
        var a = input.apparel();
        if (a == null) return;
        jdbc.update(
                "UPDATE articulos SET tipo_producto=?,modelo=?,marca=?,talle=?,sistema_talle=?,color=?,material=?,publico=?,temporada=?,descripcion=? WHERE id=? AND comercio_id=?",
                a.kind(),
                a.model().trim(),
                a.brand().trim(),
                a.size().trim(),
                a.sizeSystem().trim(),
                a.color().trim(),
                a.material().trim(),
                a.audience().trim(),
                a.season().trim(),
                a.description().trim(),
                id,
                identity.storeId());
    }

    /** Activa o desactiva el artículo del comercio sin borrar su historial de operaciones. */
    @Transactional
    public Map<String, Object> setArticleActive(long id, boolean active) {
        int updated =
                jdbc.update(
                        "UPDATE articulos SET estado=? WHERE id=? AND comercio_id=?",
                        active ? "ACTIVO" : "INACTIVO",
                        id,
                        identity.storeId());
        if (updated == 0) throw new IllegalArgumentException("No se encontró el artículo.");
        return Map.of("id", id, "estado", active ? "ACTIVO" : "INACTIVO");
    }
}
