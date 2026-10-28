package ar.com.alon.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsModel {
    private static final long STORE_ID = 1L;
    private final JdbcTemplate jdbc;

    public OperationsModel(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Map<String, Object> createCustomer(CustomerInput input) {
        String name = required(input.name(), "El nombre del cliente es obligatorio.");
        String code = "CLI-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id = insert("INSERT INTO clientes (comercio_id,codigo,nombre,telefono,email,limite_credito) VALUES (?,?,?,?,?,?)",
                STORE_ID, code, name, value(input.phone()), value(input.email()), nonNegative(input.creditLimit(), "El límite de crédito no puede ser negativo."));
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    @Transactional
    public Map<String, Object> updateCustomer(long id, CustomerInput input) {
        jdbc.queryForObject("SELECT id FROM clientes WHERE id=? AND comercio_id=? FOR UPDATE", Long.class, id, STORE_ID);
        String name = required(input.name(), "El nombre del cliente es obligatorio.");
        jdbc.update("UPDATE clientes SET nombre=?,telefono=?,email=?,limite_credito=? WHERE id=? AND comercio_id=?",
                name, value(input.phone()), value(input.email()), nonNegative(input.creditLimit(), "El límite de crédito no puede ser negativo."), id, STORE_ID);
        return Map.of("id", id, "nombre", name);
    }

    @Transactional
    public Map<String, Object> setCustomerActive(long id, boolean active) {
        int changed = jdbc.update("UPDATE clientes SET estado=? WHERE id=? AND comercio_id=?", active ? "ACTIVO" : "INACTIVO", id, STORE_ID);
        if (changed == 0) throw new IllegalArgumentException("No se encontró el cliente.");
        return Map.of("id", id, "estado", active ? "ACTIVO" : "INACTIVO");
    }

    @Transactional
    public Map<String, Object> createSupplier(SupplierInput input) {
        String name = required(input.name(), "El nombre del proveedor es obligatorio.");
        String code = "PROV-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id = insert("INSERT INTO proveedores (comercio_id,codigo,nombre,cuit,especialidad,telefono,email) VALUES (?,?,?,?,?,?,?)",
                STORE_ID, code, name, value(input.taxId()), value(input.specialty()), value(input.phone()), value(input.email()));
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    @Transactional
    public Map<String, Object> updateSupplier(long id, SupplierInput input) {
        jdbc.queryForObject("SELECT id FROM proveedores WHERE id=? AND comercio_id=? FOR UPDATE", Long.class, id, STORE_ID);
        String name = required(input.name(), "El nombre del proveedor es obligatorio.");
        jdbc.update("UPDATE proveedores SET nombre=?,cuit=?,especialidad=?,telefono=?,email=? WHERE id=? AND comercio_id=?",
                name, value(input.taxId()), value(input.specialty()), value(input.phone()), value(input.email()), id, STORE_ID);
        return Map.of("id", id, "nombre", name);
    }

    @Transactional
    public Map<String, Object> setSupplierActive(long id, boolean active) {
        int changed = jdbc.update("UPDATE proveedores SET estado=? WHERE id=? AND comercio_id=?", active ? "ACTIVO" : "INACTIVO", id, STORE_ID);
        if (changed == 0) throw new IllegalArgumentException("No se encontró el proveedor.");
        return Map.of("id", id, "estado", active ? "ACTIVO" : "INACTIVO");
    }

    @Transactional
    public Map<String, Object> createArticle(ArticleInput input) {
        String code = required(input.code(), "El código del artículo es obligatorio.");
        String name = required(input.name(), "El nombre del artículo es obligatorio.");
        Integer duplicateName = jdbc.queryForObject("SELECT COUNT(*) FROM articulos WHERE comercio_id=? AND LOWER(TRIM(nombre))=LOWER(TRIM(?))", Integer.class, STORE_ID, name);
        if (duplicateName != null && duplicateName > 0) throw new IllegalArgumentException("Ya existe un artículo con ese nombre. Buscalo en el listado de artículos.");
        assertBelongs("rubros", input.categoryId());
        assertBelongs("proveedores", input.supplierId());
        BigDecimal stock = nonNegative(input.initialStock(), "El stock inicial no puede ser negativo.");
        BigDecimal cost = nonNegative(input.cost(), "El costo no puede ser negativo.");
        BigDecimal freight = nonNegative(input.freightPerUnit() == null ? BigDecimal.ZERO : input.freightPerUnit(), "El flete por unidad no puede ser negativo.");
        BigDecimal freightPercent = nonNegative(input.freightPercent() == null ? BigDecimal.ZERO : input.freightPercent(), "El porcentaje de flete no puede ser negativo.");
        if (input.freightByValue() && input.freightByPercent()) throw new IllegalArgumentException("Elegí flete por valor fijo o por porcentaje, no ambos.");
        if (!input.freightByValue()) freight = BigDecimal.ZERO;
        if (!input.freightByPercent()) freightPercent = BigDecimal.ZERO;
        BigDecimal margin = nonNegative(input.marginPercent() == null ? BigDecimal.ZERO : input.marginPercent(), "El margen no puede ser negativo.");
        boolean roundPrice = input.roundPrice();
        BigDecimal salePrice = calculateSalePrice(cost, freight, freightPercent, margin, roundPrice);
        long id = insert("INSERT INTO articulos (comercio_id,codigo,nombre,rubro_id,proveedor_principal_id,unidad,costo,flete_unitario,flete_porcentaje,margen_porcentaje,redondeo_precio,precio_venta,stock,stock_minimo) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                STORE_ID, code, name, input.categoryId(), input.supplierId(), value(input.unit()).isBlank() ? "UNIDAD" : input.unit().trim(),
                cost, freight, freightPercent, margin, roundPrice, salePrice, stock,
                nonNegative(input.minimumStock(), "El stock mínimo no puede ser negativo."));
        if (stock.signum() > 0) addStockMovement(id, "ALTA", stock, "ALTA-" + code, "Stock inicial del artículo");
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    @Transactional
    public Map<String, Object> updateArticle(long id, ArticleInput input) {
        Map<String, Object> current = jdbc.queryForMap("SELECT id,codigo,nombre,stock FROM articulos WHERE id=? AND comercio_id=? FOR UPDATE", id, STORE_ID);
        String code = required(input.code(), "El código del artículo es obligatorio.");
        String name = required(input.name(), "El nombre del artículo es obligatorio.");
        Integer duplicateName = jdbc.queryForObject("SELECT COUNT(*) FROM articulos WHERE comercio_id=? AND LOWER(TRIM(nombre))=LOWER(TRIM(?)) AND id<>?", Integer.class, STORE_ID, name, id);
        if (duplicateName != null && duplicateName > 0) throw new IllegalArgumentException("Ya existe otro artículo con ese nombre.");
        assertBelongs("rubros", input.categoryId()); assertBelongs("proveedores", input.supplierId());
        BigDecimal cost = nonNegative(input.cost(), "El costo no puede ser negativo.");
        BigDecimal freight = nonNegative(input.freightPerUnit() == null ? BigDecimal.ZERO : input.freightPerUnit(), "El flete no puede ser negativo.");
        BigDecimal freightPercent = nonNegative(input.freightPercent() == null ? BigDecimal.ZERO : input.freightPercent(), "El porcentaje de flete no puede ser negativo.");
        if (input.freightByValue() && input.freightByPercent()) throw new IllegalArgumentException("Elegí un solo tipo de flete.");
        if (!input.freightByValue()) freight = BigDecimal.ZERO;
        if (!input.freightByPercent()) freightPercent = BigDecimal.ZERO;
        BigDecimal margin = nonNegative(input.marginPercent() == null ? BigDecimal.ZERO : input.marginPercent(), "El margen no puede ser negativo.");
        BigDecimal price = input.salePrice() == null ? calculateSalePrice(cost, freight, freightPercent, margin, input.roundPrice()) : nonNegative(input.salePrice(), "El precio no puede ser negativo.");
        BigDecimal oldStock = new BigDecimal(current.get("stock").toString());
        BigDecimal requestedStock = input.initialStock() == null ? oldStock : input.initialStock();
        if (requestedStock.signum() < 0) throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        BigDecimal newStock = requestedStock.setScale(3, RoundingMode.HALF_UP);
        jdbc.update("UPDATE articulos SET codigo=?,nombre=?,rubro_id=?,proveedor_principal_id=?,unidad=?,costo=?,flete_unitario=?,flete_porcentaje=?,margen_porcentaje=?,redondeo_precio=?,precio_venta=?,stock_minimo=? WHERE id=? AND comercio_id=?",
                code, name, input.categoryId(), input.supplierId(), value(input.unit()).isBlank() ? "UNIDAD" : input.unit().trim(), cost, freight, freightPercent, margin, input.roundPrice(), price,
                nonNegative(input.minimumStock(), "El stock mínimo no puede ser negativo."), id, STORE_ID);
        jdbc.update("UPDATE articulos SET stock=? WHERE id=? AND comercio_id=?", newStock, id, STORE_ID);
        if (oldStock.compareTo(newStock) != 0) addStockMovement(id, "AJUSTE", oldStock.subtract(newStock).abs(), "AJUSTE-" + id, "Cantidad ajustada de " + oldStock.toPlainString() + " a " + newStock.toPlainString());
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    @Transactional
    public Map<String, Object> setArticleActive(long id, boolean active) {
        int updated = jdbc.update("UPDATE articulos SET estado=? WHERE id=? AND comercio_id=?", active ? "ACTIVO" : "INACTIVO", id, STORE_ID);
        if (updated == 0) throw new IllegalArgumentException("No se encontró el artículo.");
        return Map.of("id", id, "estado", active ? "ACTIVO" : "INACTIVO");
    }

    @Transactional
    public Map<String, Object> createCategory(CategoryInput input) {
        String name = required(input.name(), "El nombre del rubro es obligatorio.");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM rubros WHERE comercio_id=? AND LOWER(TRIM(nombre))=LOWER(TRIM(?))", Integer.class, STORE_ID, name);
        if (exists != null && exists > 0) throw new IllegalArgumentException("Ya existe un rubro con ese nombre.");
        String code = "RUB-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long nextId = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM rubros", Long.class);
        long id = nextId == null ? 1L : nextId;
        jdbc.update("INSERT INTO rubros (id,comercio_id,codigo,nombre) VALUES (?,?,?,?)", id, STORE_ID, code, name);
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    @Transactional
    public Map<String, Object> createSale(SaleInput input) {
        assertBelongs("clientes", input.customerId());
        LocalDate date = input.date() == null ? LocalDate.now() : input.date();
        String saleType = enumValue(input.saleType(), "CONTADO", "CONTADO", "CUENTA_CORRIENTE");
        String payment = enumValue(input.paymentMethod(), "EFECTIVO", "EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO", "CUENTA_CORRIENTE");
        if (saleType.equals("CUENTA_CORRIENTE")) payment = "CUENTA_CORRIENTE";
        else if (payment.equals("CUENTA_CORRIENTE")) throw new IllegalArgumentException("Elegí cuenta corriente como tipo de venta para registrar una deuda.");
        if (input.lines() == null || input.lines().isEmpty()) throw new IllegalArgumentException("Agregá al menos un artículo a la venta.");

        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, total = BigDecimal.ZERO, costTotal = BigDecimal.ZERO;
        record PricedLine(long articleId, BigDecimal quantity, BigDecimal price, BigDecimal cost, BigDecimal discountPercent, BigDecimal lineTotal) {}
        java.util.ArrayList<PricedLine> lines = new java.util.ArrayList<>();
        java.util.Map<Long, BigDecimal> reservedStock = new java.util.HashMap<>();
        for (SaleLine line : input.lines()) {
            BigDecimal quantity = positiveQuantity(line.quantity(), "La cantidad vendida debe ser mayor a cero.");
            Map<String, Object> article = lockArticle(line.articleId());
            BigDecimal stock = new BigDecimal(article.get("stock").toString());
            BigDecimal reserved = reservedStock.getOrDefault(line.articleId(), BigDecimal.ZERO).add(quantity);
            if (stock.compareTo(reserved) < 0) throw new IllegalStateException("Stock insuficiente para " + article.get("nombre") + ". Disponible: " + stock.toPlainString());
            reservedStock.put(line.articleId(), reserved);
            BigDecimal price = new BigDecimal(article.get("precio_venta").toString());
            BigDecimal cost = new BigDecimal(article.get("costo").toString());
            BigDecimal percent = nonNegative(line.discountPercent() == null ? BigDecimal.ZERO : line.discountPercent(), "El descuento no puede ser negativo.");
            if (percent.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("El descuento no puede superar el 100%.");
            BigDecimal gross = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineDiscount = gross.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = gross.subtract(lineDiscount);
            subtotal = subtotal.add(gross); discount = discount.add(lineDiscount); total = total.add(lineTotal); costTotal = costTotal.add(quantity.multiply(cost));
            lines.add(new PricedLine(line.articleId(), quantity, price, cost, percent, lineTotal));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP); discount = discount.setScale(2, RoundingMode.HALF_UP);
        total = total.setScale(2, RoundingMode.HALF_UP); costTotal = costTotal.setScale(2, RoundingMode.HALF_UP);
        if (saleType.equals("CUENTA_CORRIENTE")) {
            Map<String, Object> customer = jdbc.queryForMap("SELECT saldo_cuenta_corriente,limite_credito FROM clientes WHERE id=? AND comercio_id=? FOR UPDATE", input.customerId(), STORE_ID);
            BigDecimal due = new BigDecimal(customer.get("saldo_cuenta_corriente").toString()).add(total);
            if (due.compareTo(new BigDecimal(customer.get("limite_credito").toString())) > 0) throw new IllegalStateException("La venta supera el límite de crédito disponible del cliente.");
        }
        Long cashId = null;
        if (!payment.equals("CUENTA_CORRIENTE")) cashId = requireOpenCash(date);
        String number = "V-" + date.toString().replace("-", "") + "-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long saleId = insert("INSERT INTO ventas (comercio_id,numero,fecha,fecha_hora,cliente_id,tipo_venta,medio_pago,tipo_comprobante,subtotal,descuento,total,costo_total) VALUES (?,?,?,?,?,?,?,'INTERNO_NO_FISCAL',?,?,?,?)",
                STORE_ID, number, Date.valueOf(date), Timestamp.valueOf(date.atTime(LocalTime.now())), input.customerId(), saleType, payment, subtotal, discount, total, costTotal);
        int row = 1;
        for (PricedLine line : lines) {
            jdbc.update("INSERT INTO venta_detalle (venta_id,renglon,articulo_id,cantidad,precio_unitario,costo_unitario,descuento_pct,total_linea) VALUES (?,?,?,?,?,?,?,?)",
                    saleId, row++, line.articleId(), line.quantity(), line.price(), line.cost(), line.discountPercent(), line.lineTotal());
            jdbc.update("UPDATE articulos SET stock=stock-? WHERE id=? AND comercio_id=?", line.quantity(), line.articleId(), STORE_ID);
            addStockMovement(line.articleId(), "VENTA", line.quantity().negate(), number, "Salida por venta");
        }
        if (saleType.equals("CUENTA_CORRIENTE")) jdbc.update("UPDATE clientes SET saldo_cuenta_corriente=saldo_cuenta_corriente+? WHERE id=? AND comercio_id=?", total, input.customerId(), STORE_ID);
        else addCashMovement(cashId, "INGRESO", "VENTA", payment, date, total, number, "Cobro de venta");
        return Map.of("id", saleId, "numero", number, "total", total, "tipo_comprobante", "INTERNO_NO_FISCAL");
    }

    @Transactional
    public Map<String, Object> cancelSale(long id) {
        Map<String,Object> sale = jdbc.queryForMap("SELECT id,numero,fecha,cliente_id,tipo_venta,medio_pago,total,estado FROM ventas WHERE id=? AND comercio_id=? FOR UPDATE", id, STORE_ID);
        String status = String.valueOf(sale.get("estado"));
        if (!"CONFIRMADA".equals(status)) throw new IllegalStateException("Esta venta ya está cancelada o no se puede cancelar.");
        BigDecimal total = new BigDecimal(sale.get("total").toString());
        String paymentMethod = String.valueOf(sale.get("medio_pago"));
        boolean currentAccount = "CUENTA_CORRIENTE".equals(paymentMethod);
        if (currentAccount) {
            Map<String,Object> customer = jdbc.queryForMap("SELECT saldo_cuenta_corriente FROM clientes WHERE id=? AND comercio_id=? FOR UPDATE", sale.get("cliente_id"), STORE_ID);
            BigDecimal balance = new BigDecimal(customer.get("saldo_cuenta_corriente").toString());
            if (balance.compareTo(total) < 0) throw new IllegalStateException("No se puede cancelar: la deuda de esta venta ya fue saldada total o parcialmente.");
            jdbc.update("UPDATE clientes SET saldo_cuenta_corriente=saldo_cuenta_corriente-? WHERE id=? AND comercio_id=?", total, sale.get("cliente_id"), STORE_ID);
        }
        Long cashId = null;
        if (!currentAccount) {
            List<Long> originalCash = jdbc.query("SELECT id FROM movimientos_caja WHERE comercio_id=? AND categoria='VENTA' AND tipo='INGRESO' AND referencia=? AND medio_pago=? ORDER BY id LIMIT 1 FOR UPDATE",
                    (rs, row) -> rs.getLong(1), STORE_ID, sale.get("numero"), paymentMethod);
            if (originalCash.isEmpty()) throw new IllegalStateException("No se encontró el movimiento de caja original de esta venta.");
            LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
            cashId = requireOpenCash(today);
        }
        List<Map<String,Object>> lines = jdbc.queryForList("SELECT articulo_id,cantidad FROM venta_detalle WHERE venta_id=? ORDER BY renglon", id);
        if (lines.isEmpty()) throw new IllegalStateException("La venta no tiene artículos para devolver al stock.");
        LocalDate reversalDate = LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        for (Map<String,Object> line : lines) {
            long articleId = ((Number)line.get("articulo_id")).longValue();
            BigDecimal quantity = new BigDecimal(line.get("cantidad").toString());
            Map<String,Object> article = jdbc.queryForMap("SELECT id FROM articulos WHERE id=? AND comercio_id=? FOR UPDATE", articleId, STORE_ID);
            jdbc.update("UPDATE articulos SET stock=stock+? WHERE id=? AND comercio_id=?", quantity, articleId, STORE_ID);
            addStockMovement(articleId, "ANULACION_VENTA", quantity, "ANUL-" + sale.get("numero"), "Devolución de stock por cancelación de venta");
        }
        if (!currentAccount) addCashMovement(cashId, "EGRESO", "VENTA_ANULADA", paymentMethod, reversalDate, total,
                "ANUL-" + sale.get("numero"), "Devolución del cobro por cancelación de venta " + sale.get("numero"));
        jdbc.update("UPDATE ventas SET estado='CANCELADA' WHERE id=? AND comercio_id=?", id, STORE_ID);
        return Map.of("id", id, "numero", sale.get("numero"), "estado", "CANCELADA", "total_reintegrado", total, "articulos_devueltos", lines.size());
    }

    @Transactional
    public Map<String, Object> createPurchase(PurchaseInput input) {
        assertBelongs("proveedores", input.supplierId());
        if (input.lines() == null || input.lines().isEmpty()) throw new IllegalArgumentException("Ingresá al menos un artículo en la factura.");
        LocalDate date = input.date() == null ? LocalDate.now() : input.date();
        String docType = enumValue(input.documentType(), "FACTURA", "FACTURA", "REMITO");
        String docNumber = required(input.documentNumber(), "El número de comprobante es obligatorio.");
        BigDecimal extras = nonNegative(input.extraCosts() == null ? BigDecimal.ZERO : input.extraCosts(), "Los gastos adicionales no pueden ser negativos.");
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        record PreparedLine(PurchaseLineInput line, BigDecimal quantity, BigDecimal cost, BigDecimal lineTotal, Map<String, Object> article) {}
        java.util.ArrayList<PreparedLine> lines = new java.util.ArrayList<>();
        for (PurchaseLineInput line : input.lines()) {
            if (line == null) throw new IllegalArgumentException("Hay un artículo incompleto en la factura.");
            BigDecimal quantity = positiveQuantity(line.quantity(), "La cantidad comprada debe ser mayor a cero.");
            BigDecimal cost = nonNegative(line.unitCost(), "El costo unitario no puede ser negativo.");
            Map<String, Object> article = lockArticle(line.articleId());
            BigDecimal lineTotal = quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            totalQuantity = totalQuantity.add(quantity);
            lines.add(new PreparedLine(line, quantity, cost, lineTotal, article));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(extras).setScale(2, RoundingMode.HALF_UP);
        String paymentType = enumValue(input.paymentType(), "CONTADO", "CONTADO", "CUENTA_CORRIENTE");
        BigDecimal paid = "CONTADO".equals(paymentType) ? total : nonNegative(input.paid() == null ? BigDecimal.ZERO : input.paid(), "El importe pagado no puede ser negativo.");
        if (paid.compareTo(total) > 0) throw new IllegalArgumentException("El pago no puede superar el total de la compra.");
        Long cashId = paid.signum() > 0 ? requireOpenCash(date) : null;
        long purchaseId = insert("INSERT INTO compras (comercio_id,proveedor_id,tipo_documento,numero_documento,fecha,subtotal,gastos_adicionales,total,pagado,saldo,estado) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                STORE_ID, input.supplierId(), docType, docNumber, Date.valueOf(date), subtotal, extras, total, paid, total.subtract(paid), paid.compareTo(total) == 0 ? "PAGADA" : "PENDIENTE");
        BigDecimal allocatedExtras = BigDecimal.ZERO;
        int row = 1;
        for (PreparedLine prepared : lines) {
            BigDecimal allocated;
            if (row == lines.size()) allocated = extras.subtract(allocatedExtras);
            else {
                BigDecimal weight = subtotal.signum() > 0 ? prepared.lineTotal().divide(subtotal, 8, RoundingMode.HALF_UP) : prepared.quantity().divide(totalQuantity, 8, RoundingMode.HALF_UP);
                allocated = extras.multiply(weight).setScale(2, RoundingMode.HALF_UP);
                allocatedExtras = allocatedExtras.add(allocated);
            }
            BigDecimal landedUnitCost = prepared.cost().add(allocated.divide(prepared.quantity(), 2, RoundingMode.HALF_UP));
            jdbc.update("INSERT INTO compra_detalle (compra_id,renglon,articulo_id,cantidad,costo_unitario,total_linea) VALUES (?,?,?,?,?,?)",
                    purchaseId, row++, prepared.line().articleId(), prepared.quantity(), prepared.cost(), prepared.lineTotal());
            Map<String, Object> article = lockArticle(prepared.line().articleId());
            BigDecimal stock = new BigDecimal(article.get("stock").toString());
            BigDecimal oldCost = new BigDecimal(article.get("costo").toString());
            BigDecimal weightedCost = stock.add(prepared.quantity()).signum() == 0 ? landedUnitCost : stock.multiply(oldCost).add(prepared.quantity().multiply(landedUnitCost)).divide(stock.add(prepared.quantity()), 2, RoundingMode.HALF_UP);
            BigDecimal freight = new BigDecimal(article.get("flete_unitario").toString());
            BigDecimal freightPercent = new BigDecimal(article.get("flete_porcentaje").toString());
            BigDecimal margin = new BigDecimal(article.get("margen_porcentaje").toString());
            Object roundFlag = article.get("redondeo_precio");
            boolean roundPrice = roundFlag instanceof Boolean flag ? flag : roundFlag instanceof Number number && number.intValue() != 0;
            BigDecimal salePrice = calculateSalePrice(weightedCost, freight, freightPercent, margin, roundPrice);
            jdbc.update("UPDATE articulos SET stock=stock+?,costo=?,precio_venta=? WHERE id=? AND comercio_id=?", prepared.quantity(), weightedCost, salePrice, prepared.line().articleId(), STORE_ID);
            addStockMovement(prepared.line().articleId(), "COMPRA", prepared.quantity(), docNumber, "Ingreso por " + docType.toLowerCase(java.util.Locale.ROOT));
        }
        jdbc.update("UPDATE proveedores SET saldo_actual=saldo_actual+? WHERE id=? AND comercio_id=?", total.subtract(paid), input.supplierId(), STORE_ID);
        if (paid.signum() > 0) {
            String receipt = "PAG-COMPRA-" + purchaseId;
            String paymentMethod = enumValue(input.paymentMethod(), "EFECTIVO", "EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO");
            insert("INSERT INTO pagos_proveedores (comercio_id,compra_id,proveedor_id,caja_id,fecha,medio_pago,importe,numero_recibo) VALUES (?,?,?,?,?,?,?,?)",
                    STORE_ID, purchaseId, input.supplierId(), cashId, Date.valueOf(date), paymentMethod, paid, receipt);
            addCashMovement(cashId, "EGRESO", "PAGO_PROVEEDOR", paymentMethod, date, paid, receipt, "Pago de compra " + docNumber);
        }
        return Map.of("id", purchaseId, "numero_documento", docNumber, "articulos", lines.size(), "subtotal", subtotal, "total", total, "saldo", total.subtract(paid));
    }

    @Transactional
    public Map<String, Object> updatePurchase(long id, PurchaseInput input) {
        Map<String, Object> current = jdbc.queryForMap("SELECT proveedor_id,fecha,total,pagado,numero_documento FROM compras WHERE id=? AND comercio_id=? FOR UPDATE", id, STORE_ID);
        long supplierId = ((Number) current.get("proveedor_id")).longValue();
        if (input.supplierId() != supplierId) throw new IllegalArgumentException("No se puede cambiar el proveedor de una factura existente.");
        if (input.lines() == null || input.lines().isEmpty()) throw new IllegalArgumentException("Ingresá al menos un artículo en la factura.");
        BigDecimal extras = nonNegative(input.extraCosts() == null ? BigDecimal.ZERO : input.extraCosts(), "Los gastos adicionales no pueden ser negativos.");
        Map<Long, BigDecimal[]> newLines = new java.util.LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int row = 1;
        for (PurchaseLineInput line : input.lines()) {
            BigDecimal qty = positiveQuantity(line.quantity(), "La cantidad comprada debe ser mayor a cero.");
            BigDecimal cost = nonNegative(line.unitCost(), "El costo unitario no puede ser negativo.");
            lockArticle(line.articleId());
            BigDecimal lineTotal = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            BigDecimal[] previous = newLines.get(line.articleId());
            if (previous == null) newLines.put(line.articleId(), new BigDecimal[]{qty, cost, lineTotal});
            else {
                BigDecimal mergedQuantity = previous[0].add(qty);
                BigDecimal mergedTotal = previous[2].add(lineTotal);
                newLines.put(line.articleId(), new BigDecimal[]{mergedQuantity, mergedTotal.divide(mergedQuantity, 8, RoundingMode.HALF_UP), mergedTotal});
            }
            row++;
        }
        BigDecimal total = subtotal.add(extras).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = new BigDecimal(current.get("pagado").toString());
        if (total.compareTo(paid) < 0) throw new IllegalArgumentException("El total no puede ser menor a lo que ya se pagó de esta factura.");
        List<Map<String,Object>> oldLines = jdbc.queryForList("SELECT articulo_id,cantidad FROM compra_detalle WHERE compra_id=?", id);
        Map<Long, BigDecimal> oldQuantities = new java.util.HashMap<>();
        oldLines.forEach(line -> oldQuantities.put(((Number)line.get("articulo_id")).longValue(), new BigDecimal(line.get("cantidad").toString())));
        java.util.Set<Long> articleIds = new java.util.LinkedHashSet<>(oldQuantities.keySet()); articleIds.addAll(newLines.keySet());
        for (Long articleId : articleIds) {
            BigDecimal before = oldQuantities.getOrDefault(articleId, BigDecimal.ZERO);
            BigDecimal after = newLines.containsKey(articleId) ? newLines.get(articleId)[0] : BigDecimal.ZERO;
            BigDecimal delta = after.subtract(before);
            Map<String,Object> article = lockArticle(articleId);
            BigDecimal stock = new BigDecimal(article.get("stock").toString());
            if (stock.add(delta).signum() < 0) throw new IllegalArgumentException("No se puede reducir la cantidad: el stock actual ya fue vendido o ajustado.");
            if (delta.signum() > 0) {
                BigDecimal cost = newLines.get(articleId)[1];
                BigDecimal oldCost = new BigDecimal(article.get("costo").toString());
                BigDecimal weighted = stock.add(delta).signum() == 0 ? cost : stock.multiply(oldCost).add(delta.multiply(cost)).divide(stock.add(delta), 2, RoundingMode.HALF_UP);
                BigDecimal freight = new BigDecimal(article.get("flete_unitario").toString());
                BigDecimal freightPercent = new BigDecimal(article.get("flete_porcentaje").toString());
                BigDecimal margin = new BigDecimal(article.get("margen_porcentaje").toString());
                Object roundFlag = article.get("redondeo_precio");
                boolean roundPrice = roundFlag instanceof Boolean flag ? flag : roundFlag instanceof Number number && number.intValue() != 0;
                jdbc.update("UPDATE articulos SET stock=?,costo=?,precio_venta=? WHERE id=? AND comercio_id=?", stock.add(delta), weighted, calculateSalePrice(weighted, freight, freightPercent, margin, roundPrice), articleId, STORE_ID);
            } else jdbc.update("UPDATE articulos SET stock=? WHERE id=? AND comercio_id=?", stock.add(delta), articleId, STORE_ID);
            if (delta.signum() != 0) addStockMovement(articleId, "AJUSTE_COMPRA", delta, current.get("numero_documento").toString(), "Ajuste por modificación de factura");
        }
        jdbc.update("DELETE FROM compra_detalle WHERE compra_id=?", id);
        BigDecimal allocatedExtras = BigDecimal.ZERO; row = 1;
        for (Map.Entry<Long,BigDecimal[]> entry : newLines.entrySet()) {
            BigDecimal[] values = entry.getValue();
            BigDecimal lineTotal = values[2];
            BigDecimal allocated = row == newLines.size() ? extras.subtract(allocatedExtras) : subtotal.signum() > 0 ? extras.multiply(lineTotal).divide(subtotal, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            allocatedExtras = allocatedExtras.add(allocated);
            jdbc.update("INSERT INTO compra_detalle (compra_id,renglon,articulo_id,cantidad,costo_unitario,total_linea) VALUES (?,?,?,?,?,?)", id, row++, entry.getKey(), values[0], values[1], lineTotal);
        }
        BigDecimal saldo = total.subtract(paid);
        String status = saldo.signum() == 0 ? "PAGADA" : paid.signum() > 0 ? "PARCIAL" : "PENDIENTE";
        jdbc.update("UPDATE compras SET tipo_documento=?,numero_documento=?,fecha=?,subtotal=?,gastos_adicionales=?,total=?,saldo=?,estado=? WHERE id=? AND comercio_id=?",
                enumValue(input.documentType(), "FACTURA", "FACTURA", "REMITO"), required(input.documentNumber(), "El número de comprobante es obligatorio."),
                Date.valueOf(input.date() == null ? LocalDate.now() : input.date()), subtotal, extras, total, saldo, status, id, STORE_ID);
        BigDecimal oldBalance = new BigDecimal(current.get("total").toString()).subtract(paid);
        jdbc.update("UPDATE proveedores SET saldo_actual=saldo_actual+? WHERE id=? AND comercio_id=?", saldo.subtract(oldBalance), supplierId, STORE_ID);
        return Map.of("id", id, "total", total, "pagado", paid, "saldo", saldo);
    }

    @Transactional
    public Map<String, Object> createSupplierReceipt(SupplierReceiptInput input) {
        assertBelongs("proveedores", input.supplierId());
        LocalDate date = input.date() == null ? LocalDate.now() : input.date();
        String method = enumValue(input.paymentMethod(), "EFECTIVO", "EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO");
        BigDecimal amount = positive(input.amount(), "El importe del recibo debe ser mayor a cero.");
        Map<String,Object> supplier = jdbc.queryForMap("SELECT id,nombre,saldo_actual FROM proveedores WHERE id=? AND comercio_id=? FOR UPDATE", input.supplierId(), STORE_ID);
        BigDecimal supplierBalance = new BigDecimal(supplier.get("saldo_actual").toString());
        if (amount.compareTo(supplierBalance) > 0) throw new IllegalArgumentException("El importe supera el saldo pendiente del proveedor.");
        Long cashId = requireOpenCash(date);
        List<Map<String,Object>> invoices = jdbc.queryForList("SELECT id,numero_documento,total,pagado,saldo FROM compras WHERE comercio_id=? AND proveedor_id=? AND saldo>0 ORDER BY fecha,id FOR UPDATE", STORE_ID, input.supplierId());
        String receipt = "REC-" + date.toString().replace("-", "") + "-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        BigDecimal remaining = amount;
        int applied = 0;
        for (Map<String,Object> invoice : invoices) {
            if (remaining.signum() == 0) break;
            BigDecimal due = new BigDecimal(invoice.get("saldo").toString());
            BigDecimal appliedAmount = remaining.min(due).setScale(2, RoundingMode.HALF_UP);
            long purchaseId = ((Number) invoice.get("id")).longValue();
            BigDecimal paidTotal = new BigDecimal(invoice.get("pagado").toString()).add(appliedAmount);
            BigDecimal balance = due.subtract(appliedAmount);
            jdbc.update("UPDATE compras SET pagado=?,saldo=?,estado=? WHERE id=?", paidTotal, balance, balance.signum() == 0 ? "PAGADA" : "PENDIENTE", purchaseId);
            insert("INSERT INTO pagos_proveedores (comercio_id,compra_id,proveedor_id,caja_id,fecha,medio_pago,importe,numero_recibo) VALUES (?,?,?,?,?,?,?,?)",
                    STORE_ID, purchaseId, input.supplierId(), cashId, Date.valueOf(date), method, appliedAmount, receipt);
            remaining = remaining.subtract(appliedAmount);
            applied++;
        }
        if (remaining.signum() > 0) throw new IllegalStateException("No se encontró una factura pendiente para aplicar el pago completo.");
        jdbc.update("UPDATE proveedores SET saldo_actual=saldo_actual-? WHERE id=? AND comercio_id=?", amount, input.supplierId(), STORE_ID);
        addCashMovement(cashId, "EGRESO", "PAGO_PROVEEDOR", method, date, amount, receipt, "Recibo de pago a " + supplier.get("nombre"));
        return Map.of("recibo", receipt, "proveedor", supplier.get("nombre"), "proveedor_id", input.supplierId(), "fecha", date.toString(), "medio_pago", method, "importe", amount, "saldo_anterior", supplierBalance, "saldo_restante", supplierBalance.subtract(amount), "facturas_aplicadas", applied);
    }

    @Transactional
    public Map<String, Object> openCash(CashOpenInput input) {
        LocalDate date = input.date() == null ? LocalDate.now() : input.date();
        BigDecimal opening = nonNegative(input.openingBalance(), "El saldo inicial no puede ser negativo.");
        Integer openCount = jdbc.queryForObject("SELECT COUNT(*) FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA'", Integer.class, STORE_ID, Date.valueOf(date));
        if (openCount != null && openCount > 0) throw new IllegalStateException("Ya hay una caja abierta para esa fecha. Cerrala antes de volver a abrir.");
        Integer session = jdbc.queryForObject("SELECT COALESCE(MAX(numero_sesion),0)+1 FROM cajas WHERE comercio_id=? AND fecha=?", Integer.class, STORE_ID, Date.valueOf(date));
        int sessionNumber = session == null ? 1 : session;
        long id = insert("INSERT INTO cajas (comercio_id,fecha,numero_sesion,abierto_en,saldo_inicial,estado) VALUES (?,?,?,NOW(),?,'ABIERTA')", STORE_ID, Date.valueOf(date), sessionNumber, opening);
        return Map.of("id", id, "fecha", date.toString(), "numero_sesion", sessionNumber, "estado", "ABIERTA");
    }

    @Transactional
    public Map<String, Object> addExpense(ExpenseInput input) {
        LocalDate date = input.date() == null ? LocalDate.now() : input.date();
        BigDecimal amount = positive(input.amount(), "El importe del gasto debe ser mayor a cero.");
        String method = enumValue(input.paymentMethod(), "EFECTIVO", "EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO");
        long cashId = requireOpenCash(date);
        String description = required(input.description(), "El detalle del gasto es obligatorio.");
        long id = insert("INSERT INTO gastos (comercio_id,caja_id,fecha,categoria,descripcion,medio_pago,importe) VALUES (?,?,?,?,?,?,?)",
                STORE_ID, cashId, Date.valueOf(date), required(input.category(), "La categoría es obligatoria."), description, method, amount);
        addCashMovement(cashId, "EGRESO", "GASTO", method, date, amount, "GASTO-" + id, description);
        return Map.of("id", id, "importe", amount);
    }

    public Map<String, Object> cashSummary(LocalDate date) {
        Map<String, Object> cash = jdbc.queryForMap("SELECT id,numero_sesion,saldo_inicial FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA'", STORE_ID, Date.valueOf(date));
        long id = ((Number) cash.get("id")).longValue();
        BigDecimal opening = new BigDecimal(cash.get("saldo_inicial").toString());
        Map<String, Object> report = jdbc.queryForMap("SELECT " +
                "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) ventas_efectivo," +
                "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) ventas_mercado_pago," +
                "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN importe ELSE 0 END),0) egresos_efectivo," +
                "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN importe ELSE 0 END),0) egresos_mercado_pago," +
                "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) mercado_pago_esperado " +
                "FROM movimientos_caja WHERE caja_id=?", id);
        BigDecimal expected = opening.add(jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) FROM movimientos_caja WHERE caja_id=?", BigDecimal.class, id));
        Map<String, Object> result = new java.util.LinkedHashMap<>(report);
        result.put("id", id); result.put("fecha", date.toString()); result.put("numero_sesion", cash.get("numero_sesion")); result.put("saldo_inicial", opening); result.put("efectivo_esperado", expected);
        return result;
    }

    @Transactional
    public Map<String, Object> closeCash(CashCloseInput input) {
        LocalDate date = input.date() == null ? LocalDate.now() : input.date();
        Map<String, Object> cash = jdbc.queryForMap("SELECT id,numero_sesion,saldo_inicial FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA' FOR UPDATE", STORE_ID, Date.valueOf(date));
        long id = ((Number) cash.get("id")).longValue();
        BigDecimal expected = new BigDecimal(cash.get("saldo_inicial").toString()).add(jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) FROM movimientos_caja WHERE caja_id=?", BigDecimal.class, id));
        BigDecimal counted = nonNegative(input.countedCash(), "El efectivo contado no puede ser negativo.");
        BigDecimal difference = counted.subtract(expected);
        jdbc.update("UPDATE cajas SET cerrado_en=NOW(),efectivo_esperado=?,efectivo_contado=?,diferencia=?,estado='CERRADA' WHERE id=?", expected, counted, difference, id);
        Map<String, Object> report = jdbc.queryForMap("SELECT " +
                "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) ventas_efectivo," +
                "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) ventas_mercado_pago," +
                "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN importe ELSE 0 END),0) egresos_efectivo," +
                "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN importe ELSE 0 END),0) egresos_mercado_pago," +
                "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) mercado_pago_esperado " +
                "FROM movimientos_caja WHERE caja_id=?", id);
        BigDecimal opening = new BigDecimal(cash.get("saldo_inicial").toString());
        Map<String, Object> result = new java.util.LinkedHashMap<>(report);
        result.put("id", id); result.put("fecha", date.toString()); result.put("numero_sesion", cash.get("numero_sesion")); result.put("saldo_inicial", opening);
        result.put("efectivo_esperado", expected); result.put("efectivo_contado", counted); result.put("diferencia", difference);
        return result;
    }

    @Transactional
    public Map<String, Object> createStoreOrder(StoreOrderInput input) {
        long id = insert("INSERT INTO pedidos_tienda (comercio_id,nombre_cliente,contacto,detalle,total_estimado,estado) VALUES (?,?,?,?,?,'PENDIENTE_CONFIRMACION')",
                STORE_ID, required(input.customerName(), "El nombre del cliente es obligatorio."), required(input.contact(), "El contacto es obligatorio."),
                required(input.details(), "El detalle del pedido es obligatorio."), nonNegative(input.estimatedTotal(), "El total estimado no puede ser negativo."));
        return Map.of("id", id, "estado", "PENDIENTE_CONFIRMACION");
    }

    private Map<String, Object> lockArticle(long id) {
        return jdbc.queryForMap("SELECT id,nombre,stock,costo,flete_unitario,flete_porcentaje,margen_porcentaje,redondeo_precio,precio_venta FROM articulos WHERE id=? AND comercio_id=? AND estado='ACTIVO' FOR UPDATE", id, STORE_ID);
    }

    private void assertBelongs(String table, long id) {
        if (id <= 0) throw new IllegalArgumentException("Seleccioná un registro válido.");
        String activeClause = table.equals("rubros") ? "" : " AND estado='ACTIVO'";
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=? AND comercio_id=?" + activeClause, Integer.class, id, STORE_ID);
        if (count == null || count == 0) throw new IllegalArgumentException("El registro seleccionado no existe o está inactivo.");
    }

    private long requireOpenCash(LocalDate date) {
        List<Long> ids = jdbc.query("SELECT id FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA' FOR UPDATE", (rs, row) -> rs.getLong(1), STORE_ID, Date.valueOf(date));
        if (ids.isEmpty()) throw new IllegalStateException("Abrí la caja de esa fecha antes de registrar el movimiento.");
        return ids.getFirst();
    }

    private void addCashMovement(long cashId, String type, String category, String method, LocalDate date, BigDecimal amount, String reference, String description) {
        jdbc.update("INSERT INTO movimientos_caja (comercio_id,caja_id,tipo,categoria,medio_pago,fecha_hora,importe,referencia,descripcion) VALUES (?,?,?,?,?,?,?,?,?)",
                STORE_ID, cashId, type, category, method, Timestamp.valueOf(date.atTime(LocalTime.now())), amount, reference, description);
    }

    private void addStockMovement(long articleId, String type, BigDecimal quantity, String reference, String description) {
        jdbc.update("INSERT INTO movimientos_stock (comercio_id,articulo_id,tipo,cantidad,referencia,descripcion) VALUES (?,?,?,?,?,?)",
                STORE_ID, articleId, type, quantity, reference, description);
    }

    private long insert(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        Number key = holder.getKey();
        if (key == null) throw new IllegalStateException("No se pudo generar el identificador del registro.");
        return key.longValue();
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }
    private static String required(String value, String message) { String result = value(value); if (result.isBlank()) throw new IllegalArgumentException(message); return result; }
    private static BigDecimal positive(BigDecimal value, String message) { BigDecimal result = nonNegative(value, message); if (result.signum() == 0) throw new IllegalArgumentException(message); return result; }
    private static BigDecimal positiveQuantity(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(message);
        return value.setScale(3, RoundingMode.HALF_UP);
    }
    private static BigDecimal calculateSalePrice(BigDecimal cost, BigDecimal freight, BigDecimal freightPercent, BigDecimal marginPercent, boolean roundUp) {
        BigDecimal landedCost = cost.add(freight).add(cost.multiply(freightPercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        BigDecimal price = landedCost.multiply(BigDecimal.ONE.add(marginPercent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP);
        if (roundUp && price.signum() > 0) price = price.divide(new BigDecimal("10"), 0, RoundingMode.CEILING).multiply(new BigDecimal("10.00"));
        return price;
    }
    private static BigDecimal nonNegative(BigDecimal value, String message) { if (value == null || value.signum() < 0) throw new IllegalArgumentException(message); return value.setScale(2, RoundingMode.HALF_UP); }
    private static String enumValue(String value, String fallback, String... allowed) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        for (String candidate : allowed) if (candidate.equals(normalized)) return normalized;
        throw new IllegalArgumentException("El valor seleccionado no es válido.");
    }

    public record CustomerInput(String name, String phone, String email, BigDecimal creditLimit) {}
    public record SupplierInput(String name, String taxId, String specialty, String phone, String email) {}
    public record ArticleInput(String code, String name, long categoryId, long supplierId, String unit, BigDecimal cost, BigDecimal freightPerUnit, BigDecimal freightPercent, boolean freightByValue, boolean freightByPercent, BigDecimal marginPercent, boolean roundPrice, BigDecimal initialStock, BigDecimal minimumStock, BigDecimal salePrice) {}
    public record CategoryInput(String name) {}
    public record SaleLine(long articleId, BigDecimal quantity, BigDecimal discountPercent) {}
    public record SaleInput(long customerId, LocalDate date, String saleType, String paymentMethod, List<SaleLine> lines) {}
    public record PurchaseLineInput(long articleId, BigDecimal quantity, BigDecimal unitCost) {}
    public record PurchaseInput(long supplierId, String documentType, String documentNumber, LocalDate date, List<PurchaseLineInput> lines, BigDecimal extraCosts, BigDecimal paid, String paymentType, String paymentMethod) {}
    public record SupplierReceiptInput(long supplierId, LocalDate date, BigDecimal amount, String paymentMethod) {}
    public record CashOpenInput(LocalDate date, BigDecimal openingBalance) {}
    public record ExpenseInput(LocalDate date, String category, String description, String paymentMethod, BigDecimal amount) {}
    public record CashCloseInput(LocalDate date, BigDecimal countedCash) {}
    public record StoreOrderInput(String customerName, String contact, String details, BigDecimal estimatedTotal) {}
}
