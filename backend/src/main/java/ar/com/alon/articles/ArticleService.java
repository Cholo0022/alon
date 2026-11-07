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

    /** Crea todos los talles de una prenda juntos, sin ingresar stock fuera del comprobante. */
    @Transactional
    public java.util.List<Map<String, Object>> createPurchaseApparel(
            ar.com.alon.articles.dto.PurchaseApparelInput input) {
        return createApparelVariants(input, false);
    }

    /** Añade talles al modelo guardado sin modificar stock ni datos de variantes anteriores. */
    @Transactional
    public java.util.List<Map<String, Object>> addApparelVariants(
            long id, ar.com.alon.articles.dto.AddApparelVariantsInput input) {
        if (!identity.permits("compras")) identity.require("costos");
        var base =
                jdbc.queryForMap(
                        "SELECT * FROM articulos WHERE comercio_id=? AND id=? AND estado='ACTIVO' FOR UPDATE",
                        identity.storeId(),
                        id);
        String kind = String.valueOf(base.get("tipo_producto"));
        if (!java.util.Set.of("INDUMENTARIA", "CALZADO").contains(kind))
            throw new IllegalArgumentException("Este artículo no usa talles.");
        var existing =
                jdbc.queryForList(
                        "SELECT a.talle,a.color FROM articulos a WHERE a.comercio_id=? AND a.tipo_producto=? AND a.modelo=? AND a.marca=? AND a.sistema_talle=? AND a.publico=? AND a.temporada=? AND a.material=?",
                        identity.storeId(),
                        kind,
                        base.get("modelo"),
                        base.get("marca"),
                        base.get("sistema_talle"),
                        base.get("publico"),
                        base.get("temporada"),
                        base.get("material"));
        var seen = new java.util.HashSet<String>();
        for (var row : existing)
            seen.add(
                    String.valueOf(row.get("talle")).trim().toUpperCase(java.util.Locale.ROOT)
                            + "\n"
                            + String.valueOf(row.get("color"))
                                    .trim()
                                    .toUpperCase(java.util.Locale.ROOT));
        var errors = new java.util.LinkedHashMap<String, String>();
        for (int index = 0; index < input.variants().size(); index++) {
            var variant = input.variants().get(index);
            String key =
                    variant.size().trim().toUpperCase(java.util.Locale.ROOT)
                            + "\n"
                            + variant.color().trim().toUpperCase(java.util.Locale.ROOT);
            if (!seen.add(key)) {
                errors.put(
                        "variants[" + index + "].size",
                        "Este talle y color ya existen en la prenda.");
                errors.put("variants[" + index + "].color", "Elegí una combinación nueva.");
            }
        }
        if (!errors.isEmpty())
            throw new ar.com.alon.shared.web.FieldValidationException(
                    "Hay talles y colores que ya están cargados en esta prenda.", errors);
        String name = String.valueOf(base.get("nombre"));
        String suffix = " · " + base.get("talle") + " · " + base.get("color");
        if (name.endsWith(suffix)) name = name.substring(0, name.length() - suffix.length());
        var apparel =
                new ar.com.alon.articles.dto.ApparelInput(
                        kind,
                        String.valueOf(base.get("modelo")),
                        String.valueOf(base.get("marca")),
                        String.valueOf(base.get("talle")),
                        String.valueOf(base.get("sistema_talle")),
                        String.valueOf(base.get("color")),
                        String.valueOf(base.get("material")),
                        String.valueOf(base.get("publico")),
                        String.valueOf(base.get("temporada")),
                        base.get("descripcion") == null
                                ? name
                                : String.valueOf(base.get("descripcion")));
        var first = input.variants().getFirst();
        boolean round =
                Boolean.TRUE.equals(base.get("redondeo_precio"))
                        || "1".equals(String.valueOf(base.get("redondeo_precio")));
        boolean publish =
                Boolean.TRUE.equals(base.get("cargar_web"))
                        || "1".equals(String.valueOf(base.get("cargar_web")));
        var product =
                new ArticleInput(
                        first.code(),
                        name,
                        0,
                        0,
                        "UNIDAD",
                        (BigDecimal) base.get("costo"),
                        BigDecimal.ZERO,
                        (BigDecimal) base.get("flete_porcentaje"),
                        false,
                        true,
                        (BigDecimal) base.get("margen_porcentaje"),
                        round,
                        BigDecimal.ZERO,
                        (BigDecimal) base.get("stock_minimo"),
                        null,
                        null,
                        publish,
                        apparel);
        var saved =
                createApparelVariants(
                        new ar.com.alon.articles.dto.PurchaseApparelInput(
                                product, input.variants()),
                        true);
        for (var variant : saved) {
            long variantId = ((Number) variant.get("id")).longValue();
            jdbc.update(
                    "UPDATE articulos SET precio_venta=?,imagen_clave=?,imagen_ilustrativa=? WHERE comercio_id=? AND id=?",
                    base.get("precio_venta"),
                    base.get("imagen_clave"),
                    base.get("imagen_ilustrativa"),
                    identity.storeId(),
                    variantId);
            jdbc.update(
                    "INSERT INTO articulo_imagenes(comercio_id,articulo_id,posicion,clave,ilustrativa) SELECT comercio_id,?,posicion,clave,ilustrativa FROM articulo_imagenes WHERE comercio_id=? AND articulo_id=?",
                    variantId,
                    identity.storeId(),
                    id);
        }
        audit.record("articulo", id, "AGREGAR_TALLES", null, saved);
        return saved;
    }

    @Transactional
    public java.util.List<Map<String, Object>> updateApparelGroup(
            long id, ar.com.alon.articles.dto.ApparelGroupUpdateInput input) {
        if (!identity.permits("compras")) identity.require("costos");
        var snapshot = ApparelGroups.load(jdbc, identity.storeId(), id, true);
        if (!snapshot.token().equals(input.token()))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "La prenda cambió desde que la abriste. Volvé a cargar los datos antes de guardar para conservar el stock actualizado.");
        var requested =
                new java.util.LinkedHashMap<
                        Long, ar.com.alon.articles.dto.ApparelGroupUpdateInput.Line>();
        for (var line : input.lines())
            if (requested.put(line.id(), line) != null)
                throw new IllegalArgumentException("Hay talles repetidos en la solicitud.");
        var ids = new java.util.HashSet<Long>();
        snapshot.rows().forEach(row -> ids.add(((Number) row.get("id")).longValue()));
        if (!ids.equals(requested.keySet()))
            throw new IllegalArgumentException("Revisá la lista de talles de esta prenda.");
        var combinations = new java.util.HashSet<String>();
        for (var row : snapshot.rows()) {
            var line = requested.get(((Number) row.get("id")).longValue());
            String size =
                    line.size() == null
                            ? String.valueOf(row.get("talle"))
                            : required(line.size(), "Completá el talle.");
            String color =
                    line.color() == null
                            ? String.valueOf(row.get("color"))
                            : required(line.color(), "Completá el color.");
            if (!combinations.add(
                    size.toUpperCase(java.util.Locale.ROOT)
                            + "\n"
                            + color.toUpperCase(java.util.Locale.ROOT)))
                throw new IllegalArgumentException("Este talle y color ya están cargados.");
        }
        for (var row : snapshot.rows()) {
            long variantId = ((Number) row.get("id")).longValue();
            var line = requested.get(variantId);
            if (line.size() != null || line.color() != null) {
                String size =
                        line.size() == null
                                ? String.valueOf(row.get("talle"))
                                : required(line.size(), "Completá el talle.");
                String color =
                        line.color() == null
                                ? String.valueOf(row.get("color"))
                                : required(line.color(), "Completá el color.");
                String name = ApparelGroups.name(row) + " · " + size + " · " + color;
                jdbc.update(
                        "UPDATE articulos SET talle=?,color=?,nombre=? WHERE comercio_id=? AND id=?",
                        size,
                        color,
                        name,
                        identity.storeId(),
                        variantId);
            }
            BigDecimal quantity =
                    ar.com.alon.shared.validation.DecimalRules.quantity(
                            line.stock(), false, "El stock no puede ser negativo.");
            if (quantity.stripTrailingZeros().scale() > 0)
                throw new IllegalArgumentException("Usá cantidades enteras por talle.");
            BigDecimal delta = quantity.subtract((BigDecimal) row.get("stock"));
            if (delta.signum() != 0) {
                jdbc.update(
                        "UPDATE articulos SET stock=? WHERE comercio_id=? AND id=?",
                        quantity,
                        identity.storeId(),
                        variantId);
                stock.addStockMovement(
                        variantId, "AJUSTE", delta, "PRENDA-" + id, "Ajuste de stock por talle");
            }
            if (line.code() != null)
                jdbc.update(
                        "UPDATE articulos SET codigo=? WHERE comercio_id=? AND id=?",
                        required(line.code(), "El código no puede estar vacío."),
                        identity.storeId(),
                        variantId);
        }
        var all = new java.util.ArrayList<Map<String, Object>>(snapshot.rows());
        if (input.newVariants() != null && !input.newVariants().isEmpty())
            all.addAll(
                    addApparelVariants(
                            id,
                            new ar.com.alon.articles.dto.AddApparelVariantsInput(
                                    input.newVariants())));
        var common = input.common();
        if (common != null) {
            var base = snapshot.base();
            String model =
                    common.model() == null
                            ? String.valueOf(base.get("modelo"))
                            : required(common.model(), "Completá el modelo.");
            String brand =
                    common.brand() == null
                            ? String.valueOf(base.get("marca"))
                            : required(common.brand(), "Completá la marca.");
            String material =
                    common.material() == null
                            ? String.valueOf(base.get("material"))
                            : required(common.material(), "Completá el material.");
            String system =
                    common.sizeSystem() == null
                            ? String.valueOf(base.get("sistema_talle"))
                            : required(common.sizeSystem(), "Completá el sistema de talles.");
            String audience =
                    common.audience() == null
                            ? String.valueOf(base.get("publico"))
                            : required(common.audience(), "Completá el público.");
            String season =
                    common.season() == null
                            ? String.valueOf(base.get("temporada"))
                            : required(common.season(), "Completá la temporada.");
            var allIds = all.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
            var args =
                    new java.util.ArrayList<Object>(
                            java.util.List.of(
                                    identity.storeId(),
                                    base.get("tipo_producto"),
                                    model,
                                    brand,
                                    material,
                                    system,
                                    audience,
                                    season));
            args.addAll(allIds);
            Integer collisions =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM articulos WHERE comercio_id=? AND tipo_producto=? AND modelo=? AND marca=? AND material=? AND sistema_talle=? AND publico=? AND temporada=? AND id NOT IN ("
                                    + String.join(
                                            ",", java.util.Collections.nCopies(allIds.size(), "?"))
                                    + ")",
                            Integer.class,
                            args.toArray());
            // Las variantes inactivas del modelo se conservan fuera de esta edición.
            boolean identityChanged =
                    !model.equals(base.get("modelo"))
                            || !brand.equals(base.get("marca"))
                            || !material.equals(base.get("material"))
                            || !system.equals(base.get("sistema_talle"))
                            || !audience.equals(base.get("publico"))
                            || !season.equals(base.get("temporada"));
            if (identityChanged && collisions != null && collisions > 0)
                throw new IllegalArgumentException(
                        "Ya existe otra prenda con esos datos de modelo. Elegí una referencia diferente.");
            for (var item : all) {
                long variantId = ((Number) item.get("id")).longValue();
                var row =
                        jdbc.queryForMap(
                                "SELECT * FROM articulos WHERE comercio_id=? AND id=?",
                                identity.storeId(),
                                variantId);
                String name =
                        common.name() == null
                                ? String.valueOf(row.get("nombre"))
                                : required(common.name(), "Completá el nombre.")
                                        + " · "
                                        + row.get("talle")
                                        + " · "
                                        + row.get("color");
                if (name.length() > 180)
                    throw new IllegalArgumentException("El nombre es demasiado largo.");
                jdbc.update(
                        "UPDATE articulos SET nombre=?,modelo=?,marca=?,material=?,sistema_talle=?,publico=?,temporada=?,descripcion=?,costo=?,precio_venta=?,cargar_web=? WHERE comercio_id=? AND id=?",
                        name,
                        model,
                        brand,
                        material,
                        system,
                        audience,
                        season,
                        common.description() == null
                                ? row.get("descripcion")
                                : required(common.description(), "Completá la descripción."),
                        common.cost() == null
                                ? row.get("costo")
                                : nonNegative(common.cost(), "El costo no puede ser negativo."),
                        common.salePrice() == null
                                ? row.get("precio_venta")
                                : nonNegative(
                                        common.salePrice(), "El precio no puede ser negativo."),
                        common.publishOnWeb() == null
                                ? row.get("cargar_web")
                                : common.publishOnWeb(),
                        identity.storeId(),
                        variantId);
            }
        }
        var after = new java.util.ArrayList<Map<String, Object>>();
        for (var item : all)
            after.add(
                    jdbc.queryForMap(
                            "SELECT * FROM articulos WHERE comercio_id=? AND id=?",
                            identity.storeId(),
                            item.get("id")));
        audit.record("articulo", id, "MODIFICAR_PRENDA", snapshot.rows(), after);
        return after;
    }

    @Transactional
    public void copyApparelPhotos(long source, long target) {
        jdbc.queryForObject(
                "SELECT id FROM articulos WHERE comercio_id=? AND id=?",
                Long.class,
                identity.storeId(),
                target);
        var base =
                jdbc.queryForMap(
                        "SELECT imagen_clave,imagen_ilustrativa FROM articulos WHERE comercio_id=? AND id=?",
                        identity.storeId(),
                        source);
        jdbc.update(
                "DELETE FROM articulo_imagenes WHERE comercio_id=? AND articulo_id=?",
                identity.storeId(),
                target);
        jdbc.update(
                "INSERT INTO articulo_imagenes(comercio_id,articulo_id,posicion,clave,ilustrativa) SELECT comercio_id,?,posicion,clave,ilustrativa FROM articulo_imagenes WHERE comercio_id=? AND articulo_id=?",
                target,
                identity.storeId(),
                source);
        jdbc.update(
                "UPDATE articulos SET imagen_clave=?,imagen_ilustrativa=? WHERE comercio_id=? AND id=?",
                base.get("imagen_clave"),
                base.get("imagen_ilustrativa"),
                identity.storeId(),
                target);
    }

    /** Registra el stock inicial por talle en el alta del catálogo. */
    @Transactional
    public java.util.List<Map<String, Object>> createCatalogApparel(
            ar.com.alon.articles.dto.PurchaseApparelInput input) {
        return createApparelVariants(input, true);
    }

    private java.util.List<Map<String, Object>> createApparelVariants(
            ar.com.alon.articles.dto.PurchaseApparelInput input, boolean initialStock) {
        var base = input.product();
        if (base.apparel() == null)
            throw new IllegalArgumentException("Completá los datos de la prenda.");
        var seen = new java.util.HashSet<String>();
        var result = new java.util.ArrayList<Map<String, Object>>();
        for (var variant : input.variants()) {
            if (variant.quantity() < (initialStock ? 0 : 1))
                throw new IllegalArgumentException(
                        "Ingresá cantidades enteras válidas para cada talle.");
            String combination =
                    variant.size().trim().toUpperCase(java.util.Locale.ROOT)
                            + "\n"
                            + variant.color().trim().toUpperCase(java.util.Locale.ROOT);
            if (!seen.add(combination))
                throw new IllegalArgumentException("Hay talles y colores repetidos en la prenda.");
            var a = base.apparel();
            String name =
                    base.name().trim()
                            + " · "
                            + variant.size().trim()
                            + " · "
                            + variant.color().trim();
            if (name.length() > 180)
                throw new IllegalArgumentException("El nombre de la prenda es demasiado largo.");
            var apparel =
                    new ar.com.alon.articles.dto.ApparelInput(
                            a.kind(),
                            a.model(),
                            a.brand(),
                            variant.size(),
                            a.sizeSystem(),
                            variant.color(),
                            a.material(),
                            a.audience(),
                            a.season(),
                            a.description());
            var article =
                    new ArticleInput(
                            variant.code(),
                            name,
                            0,
                            0,
                            "UNIDAD",
                            base.cost(),
                            BigDecimal.ZERO,
                            base.freightPercent(),
                            false,
                            false,
                            base.marginPercent(),
                            base.roundPrice(),
                            initialStock ? BigDecimal.valueOf(variant.quantity()) : BigDecimal.ZERO,
                            base.minimumStock(),
                            null,
                            base.photos(),
                            base.publishOnWeb(),
                            apparel);
            var saved = new java.util.LinkedHashMap<String, Object>(createArticle(article));
            if (base.salePrice() != null)
                jdbc.update(
                        "UPDATE articulos SET precio_venta=? WHERE comercio_id=? AND id=?",
                        nonNegative(base.salePrice(), "El precio no puede ser negativo."),
                        identity.storeId(),
                        saved.get("id"));
            saved.put("quantity", variant.quantity());
            saved.put("unitCost", base.cost() == null ? BigDecimal.ZERO : base.cost());
            result.add(saved);
        }
        return result;
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
