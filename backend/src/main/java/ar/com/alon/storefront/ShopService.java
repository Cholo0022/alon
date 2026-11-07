// Muestra artículos publicados y recibe pedidos web sin descontar stock hasta su confirmación.
package ar.com.alon.storefront;

import ar.com.alon.shared.validation.DecimalRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Statement;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShopService {
    private final JdbcTemplate jdbc;
    private final ShopAccountService accounts;

    @org.springframework.beans.factory.annotation.Value("${alon.shop.hero-image:}")
    private String heroImage = "";

    /**
     * Recibe y conserva las dependencias necesarias para el catálogo público y la validación de
     * carritos y pedidos.
     */
    public ShopService(JdbcTemplate jdbc, ShopAccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    /**
     * Busca y pagina productos activos publicados en la web con stock, adjuntando sus fotos y
     * rubros disponibles.
     */
    public Map<String, Object> catalog(String query, String category, int page) {
        if (query.length() > 100 || category.length() > 180 || page < 0 || page > 10000)
            throw new IllegalArgumentException("La búsqueda no es válida.");
        String displayName =
                "CASE WHEN a.tipo_producto IN ('INDUMENTARIA','CALZADO') AND RIGHT(a.nombre,CHAR_LENGTH(CONCAT(' · ',a.talle,' · ',a.color)))=CONCAT(' · ',a.talle,' · ',a.color) THEN LEFT(a.nombre,CHAR_LENGTH(a.nombre)-CHAR_LENGTH(CONCAT(' · ',a.talle,' · ',a.color))) ELSE a.nombre END";
        String productKey =
                "CASE WHEN a.tipo_producto IN ('INDUMENTARIA','CALZADO') THEN CONCAT('v:',SHA2(CAST(JSON_ARRAY(a.tipo_producto,LOWER(TRIM(a.modelo)),LOWER(TRIM(a.marca)),LOWER(TRIM(a.sistema_talle)),LOWER(TRIM(a.material)),LOWER(TRIM(a.publico)),LOWER(TRIM(a.temporada))) AS CHAR),256)) ELSE CONCAT('a:',a.id) END";
        String published =
                "WITH published AS (SELECT a.id,a.tipo_producto,a.modelo,a.marca,a.talle,a.sistema_talle,a.color,a.material,a.publico,a.temporada,a.descripcion,a.codigo,a.nombre,a.unidad,a.precio_venta,a.stock,a.imagen_clave,a.imagen_ilustrativa,"
                        + displayName
                        + " AS nombre_publico,CASE WHEN a.tipo_producto IN ('INDUMENTARIA','CALZADO') THEN "
                        + displayName
                        + " ELSE r.nombre END AS rubro,"
                        + productKey
                        + " AS product_key FROM articulos a JOIN rubros r ON r.id=a.rubro_id AND r.comercio_id=a.comercio_id WHERE a.comercio_id=? AND a.estado='ACTIVO' AND a.cargar_web=TRUE) ";
        String matching =
                ", matched AS (SELECT product_key,MIN(id) AS id FROM published GROUP BY product_key HAVING MAX(stock)>0 AND (?='' OR MAX(LOCATE(?,CONCAT(codigo,' ',nombre,' ',modelo,' ',marca,' ',talle,' ',color))>0)>0) AND (?='' OR MAX(rubro=?)>0)) ";
        // La paginación cuenta publicaciones completas, nunca talles individuales.
        String grouped = published.trim() + matching;
        var args = new Object[] {accounts.storeId(), query, query, category, category};
        var rows =
                jdbc.queryForList(
                        grouped
                                + "SELECT p.* FROM matched m JOIN published p ON p.id=m.id ORDER BY p.nombre_publico,p.id LIMIT 24 OFFSET "
                                + (page * 24),
                        args);
        var categories =
                jdbc.queryForList(
                        published
                                + "SELECT DISTINCT rubro AS nombre FROM published WHERE stock>0 ORDER BY nombre",
                        accounts.storeId());
        if (!rows.isEmpty()) {
            var variantArgs = new ArrayList<Object>();
            variantArgs.add(accounts.storeId());
            rows.forEach(row -> variantArgs.add(row.get("product_key")));
            var variants =
                    jdbc.queryForList(
                            published
                                    + "SELECT * FROM published WHERE product_key IN ("
                                    + String.join(",", Collections.nCopies(rows.size(), "?"))
                                    + ") ORDER BY color,talle,id",
                            variantArgs.toArray());
            ar.com.alon.articles.ArticlePhotoQueries.attach(jdbc, accounts.storeId(), variants);
            Map<String, List<Map<String, Object>>> byProduct = new HashMap<>();
            for (var variant : variants)
                byProduct
                        .computeIfAbsent(
                                variant.get("product_key").toString(), ignored -> new ArrayList<>())
                        .add(variant);
            for (var row : rows) {
                var choices = byProduct.get(row.get("product_key").toString());
                var cover =
                        choices.stream()
                                .filter(choice -> ((Number) choice.get("stock")).doubleValue() > 0)
                                .findFirst()
                                .orElse(choices.getFirst());
                row.put("photos", cover.get("photos"));
                row.put("imagen_clave", cover.get("imagen_clave"));
                row.put("imagen_ilustrativa", cover.get("imagen_ilustrativa"));
                if (!"ARTICULO".equals(row.get("tipo_producto"))) {
                    row.put("nombre", row.get("nombre_publico"));
                    row.put("variants", choices);
                }
            }
        }
        return Map.of(
                "items",
                rows,
                "total",
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                grouped + "SELECT COUNT(*) FROM matched", Long.class, args)),
                "categories",
                categories,
                "store",
                jdbc.queryForMap(
                        "SELECT nombre,slogan,color_principal,color_secundario,color_fondo,plantilla_tienda,titulo_tienda,descripcion_tienda,telefono_tienda FROM comercios WHERE id=?",
                        accounts.storeId()),
                "page",
                page,
                "heroImage",
                heroImage);
    }

    public record Line(
            @Positive long articleId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2)
                    BigDecimal expectedPrice) {}

    public record Checkout(
            @NotEmpty @Size(max = 100) List<@Valid Line> lines,
            @NotBlank @Pattern(regexp = "EFECTIVO|TRANSFERENCIA|MERCADO_PAGO")
                    String paymentMethod) {}

    public record CartInput(@NotEmpty @Size(max = 100) List<@NotNull @Positive Long> ids) {}

    /**
     * Consulta precios, stock y fotos actuales de los artículos publicados que componen el carrito.
     */
    public List<Map<String, Object>> cart(CartInput input) {
        var args = new ArrayList<Object>();
        args.add(accounts.storeId());
        args.addAll(input.ids());
        return ar.com.alon.articles.ArticlePhotoQueries.attach(
                jdbc,
                accounts.storeId(),
                jdbc.queryForList(
                        "SELECT id,nombre,unidad,precio_venta,stock,imagen_clave,imagen_ilustrativa FROM articulos WHERE comercio_id=? AND estado='ACTIVO' AND cargar_web=TRUE AND id IN ("
                                + String.join(",", Collections.nCopies(input.ids().size(), "?"))
                                + ")",
                        args.toArray()));
    }

    /**
     * Valida comprador, medio, renglones, precios y stock y guarda un pedido pendiente; reutiliza
     * la respuesta si se reintenta la misma clave.
     */
    @Transactional
    public Map<String, Object> checkout(Checkout input, Map<String, Object> buyer, String key) {
        return checkoutPrepared(input, buyer, key, false);
    }

    /**
     * Conserva el contacto por DNI; cada pedido guarda los datos enviados sin alterar contactos
     * previos.
     */
    @Transactional
    public Map<String, Object> checkoutGuest(
            ar.com.alon.storefront.dto.GuestCheckoutInput input, String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,80}"))
            throw new IllegalArgumentException("Falta el identificador del pedido.");
        var submitted = input.buyer();
        if (submitted == null
                || submitted.phone() == null
                || !submitted.phone().matches("[0-9]{10}")
                || submitted.dni() == null
                || !submitted.dni().matches("[0-9]{1,12}"))
            throw new IllegalArgumentException(
                    "Ingresá un celular de diez dígitos y un DNI solo con números.");
        for (String value :
                Arrays.asList(
                        submitted.firstName(),
                        submitted.lastName(),
                        submitted.province(),
                        submitted.city()))
            if (value == null || value.isBlank())
                throw new IllegalArgumentException("Completá todos los datos de contacto.");
        long store = accounts.storeId();
        jdbc.queryForObject("SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, store);
        var existing =
                jdbc.queryForList(
                        "SELECT id FROM compradores_tienda WHERE comercio_id=? AND dni=? FOR UPDATE",
                        store,
                        submitted.dni());
        long buyerId;
        if (!existing.isEmpty()) buyerId = ((Number) existing.getFirst().get("id")).longValue();
        else {
            var holder = new GeneratedKeyHolder();
            jdbc.update(
                    connection -> {
                        var statement =
                                connection.prepareStatement(
                                        "INSERT INTO compradores_tienda(comercio_id,nombre,apellido,dni,domicilio,telefono,password_hash,codigo_postal,ciudad,provincia,pais) VALUES(?,?,?,?,'',?,NULL,'',?,?,'Argentina')",
                                        Statement.RETURN_GENERATED_KEYS);
                        statement.setLong(1, store);
                        statement.setString(2, submitted.firstName().trim());
                        statement.setString(3, submitted.lastName().trim());
                        statement.setString(4, submitted.dni());
                        statement.setString(5, submitted.phone());
                        statement.setString(6, submitted.city().trim());
                        statement.setString(7, submitted.province().trim());
                        return statement;
                    },
                    holder);
            buyerId = Objects.requireNonNull(holder.getKey()).longValue();
        }
        var buyer = new LinkedHashMap<String, Object>();
        buyer.put("id", buyerId);
        buyer.put("nombre", submitted.firstName().trim());
        buyer.put("apellido", submitted.lastName().trim());
        buyer.put("dni", submitted.dni());
        buyer.put("telefono", submitted.phone());
        buyer.put("ciudad", submitted.city().trim());
        buyer.put("provincia", submitted.province().trim());
        buyer.put("pais", "Argentina");
        return checkoutPrepared(new Checkout(input.lines(), null), buyer, key, true);
    }

    private Map<String, Object> checkoutPrepared(
            Checkout input, Map<String, Object> buyer, String key, boolean guest) {
        // El catálogo sigue visible, pero una licencia vencida impide registrar nuevos pedidos.
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM comercios WHERE id=? AND licencia_controlada=TRUE AND (licencia_hasta IS NULL OR licencia_hasta<=?)",
                        Integer.class,
                        accounts.storeId(),
                        java.time.LocalDateTime.now(
                                java.time.ZoneId.of("America/Argentina/Buenos_Aires")))
                > 0)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La tienda temporalmente no recibe pedidos. Contactá al comercio.");

        if (!guest
                && (input.paymentMethod() == null
                        || !Set.of("EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO")
                                .contains(input.paymentMethod())))
            throw new IllegalArgumentException("Elegí un medio de pago válido para el pedido.");
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,80}"))
            throw new IllegalArgumentException("Falta el identificador del pedido.");
        long store = accounts.storeId(), buyerId = ((Number) buyer.get("id")).longValue();
        jdbc.queryForObject("SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, store);
        var lines =
                input.lines().stream().sorted(Comparator.comparingLong(Line::articleId)).toList();
        if (lines.stream().map(Line::articleId).distinct().count() != lines.size())
            throw new IllegalArgumentException("Hay artículos repetidos.");
        String hash =
                digest(
                        lines.stream()
                                        .map(
                                                l ->
                                                        l.articleId()
                                                                + ":"
                                                                + l.quantity()
                                                                        .stripTrailingZeros()
                                                                        .toPlainString()
                                                                + ":"
                                                                + l.expectedPrice()
                                                                        .stripTrailingZeros()
                                                                        .toPlainString())
                                        .collect(java.util.stream.Collectors.joining(";"))
                                + ":"
                                + input.paymentMethod()
                                + (guest
                                        ? tools.jackson.databind.json.JsonMapper.builder()
                                                .build()
                                                .writeValueAsString(
                                                        List.of(
                                                                buyer.get("nombre"),
                                                                buyer.get("apellido"),
                                                                buyer.get("dni"),
                                                                buyer.get("telefono"),
                                                                buyer.get("ciudad"),
                                                                buyer.get("provincia")))
                                        : ""));
        var previous =
                jdbc.queryForList(
                        "SELECT id,estado,total_estimado,solicitud_hash FROM pedidos_tienda WHERE comercio_id=? AND comprador_id=? AND clave_solicitud=?",
                        store,
                        buyerId,
                        key);
        if (!previous.isEmpty()) {
            var saved = previous.getFirst();
            if (!hash.equals(saved.get("solicitud_hash")))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "El identificador ya corresponde a otro pedido.");
            return Map.of(
                    "id",
                    saved.get("id"),
                    "estado",
                    saved.get("estado"),
                    "total",
                    saved.get("total_estimado"));
        }
        // El pedido queda pendiente: el stock se descontará cuando el comercio lo confirme.
        BigDecimal total = BigDecimal.ZERO;
        var articles = new LinkedHashMap<Long, Map<String, Object>>();
        for (var line : lines) {
            var a =
                    jdbc.queryForMap(
                            "SELECT id,tipo_producto,nombre,unidad,stock,precio_venta,cargar_web FROM articulos WHERE comercio_id=? AND id=? AND estado='ACTIVO' FOR UPDATE",
                            store,
                            line.articleId());
            if (!"ARTICULO".equals(a.get("tipo_producto"))
                    && line.quantity().stripTrailingZeros().scale() > 0)
                throw new IllegalArgumentException(
                        "Elegí una cantidad entera de prendas o pares del talle y color seleccionados.");
            if (!Boolean.TRUE.equals(a.get("cargar_web"))
                    && !"1".equals(String.valueOf(a.get("cargar_web"))))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El artículo ya no está disponible en la web. Actualizá tu carrito.");
            if (((BigDecimal) a.get("stock")).compareTo(line.quantity()) < 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No hay stock suficiente de "
                                + a.get("nombre")
                                + ". Actualizá tu carrito.");
            var price = (BigDecimal) a.get("precio_venta");
            if (price.compareTo(line.expectedPrice()) != 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cambió el precio de "
                                + a.get("nombre")
                                + ". Revisá tu carrito antes de enviarlo.");
            total = total.add(price.multiply(line.quantity()).setScale(2, RoundingMode.HALF_UP));
            articles.put(line.articleId(), a);
        }
        total = DecimalRules.money(total, "El total excede el máximo permitido.");
        var holder = new GeneratedKeyHolder();
        BigDecimal orderTotal = total;
        jdbc.update(
                c -> {
                    var s =
                            c.prepareStatement(
                                    "INSERT INTO pedidos_tienda(comercio_id,nombre_cliente,contacto,detalle,total_estimado,comprador_id,dni,domicilio,clave_solicitud,solicitud_hash,medio_pago,provincia,ciudad) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS);
                    s.setLong(1, store);
                    s.setString(2, buyer.get("nombre") + " " + buyer.get("apellido"));
                    s.setString(3, String.valueOf(buyer.get("telefono")));
                    s.setString(4, lines.size() + " artículos desde la tienda online");
                    s.setBigDecimal(5, orderTotal);
                    s.setLong(6, buyerId);
                    s.setString(7, String.valueOf(buyer.get("dni")));
                    s.setString(
                            8,
                            java.util.stream.Stream.of(
                                            "domicilio",
                                            "codigo_postal",
                                            "ciudad",
                                            "provincia",
                                            "pais")
                                    .map(field -> Objects.toString(buyer.get(field), "").trim())
                                    .filter(value -> !value.isEmpty())
                                    .collect(java.util.stream.Collectors.joining(", ")));
                    s.setString(9, key);
                    s.setString(10, hash);
                    s.setString(11, input.paymentMethod());
                    s.setString(12, Objects.toString(buyer.get("provincia"), ""));
                    s.setString(13, Objects.toString(buyer.get("ciudad"), ""));
                    return s;
                },
                holder);
        long id = Objects.requireNonNull(holder.getKey()).longValue();
        for (var line : lines) {
            var a = articles.get(line.articleId());
            jdbc.update(
                    "INSERT INTO pedido_tienda_detalle(comercio_id,pedido_id,articulo_id,nombre_articulo,unidad,cantidad,precio_unitario) VALUES(?,?,?,?,?,?,?)",
                    store,
                    id,
                    line.articleId(),
                    a.get("nombre"),
                    a.get("unidad"),
                    line.quantity(),
                    a.get("precio_venta"));
        }
        return Map.of("id", id, "estado", "PENDIENTE_CONFIRMACION", "total", total);
    }

    /** Devuelve los últimos cien pedidos del comprador dentro del comercio de la tienda. */
    public List<Map<String, Object>> orders(long buyer) {
        return jdbc.queryForList(
                "SELECT id,estado,total_estimado,creado_en,medio_pago FROM pedidos_tienda WHERE comercio_id=? AND comprador_id=? ORDER BY id DESC LIMIT 100",
                accounts.storeId(),
                buyer);
    }

    /**
     * Calcula una huella SHA-256 hexadecimal para comparar contenidos sin conservar su
     * representación completa como clave.
     */
    private String digest(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
