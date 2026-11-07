// Recibe las solicitudes de informes del negocio y las deriva al servicio correspondiente.
package ar.com.alon.reporting;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportQueries queries;

    /** Recibe y conserva las dependencias necesarias para los informes del comercio. */
    public ReportController(ReportQueries queries) {
        this.queries = queries;
    }

    /**
     * Expone ventas y ganancia bruta agrupadas por día, con descuentos y ticket promedio mediante
     * la API, delegando la consulta al repositorio.
     */
    @GetMapping("/alon/reports/daily-sales")
    public List<Map<String, Object>> dailySales(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return queries.dailySales(from, to);
    }

    /**
     * Expone totales de ventas, costo y ganancia bruta agrupados por mes mediante la API, delegando
     * la consulta al repositorio.
     */
    @GetMapping("/alon/reports/monthly-sales")
    public List<Map<String, Object>> monthlySales(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return queries.monthlySales(from, to);
    }

    /**
     * Expone importes y operaciones de ventas agrupados por medio de pago mediante la API,
     * delegando la consulta al repositorio.
     */
    @GetMapping("/alon/reports/payment-methods")
    public List<Map<String, Object>> paymentMethods(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return queries.paymentMethods(from, to);
    }

    /**
     * Expone ranking de clientes por consumo junto con deuda y límite de crédito mediante la API,
     * delegando la consulta al repositorio.
     */
    @GetMapping("/alon/reports/top-customers")
    public List<Map<String, Object>> topCustomers(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return queries.topCustomers(from, to);
    }

    /**
     * Expone artículos más vendidos con unidades, importe y margen bruto mediante la API, delegando
     * la consulta al repositorio.
     */
    @GetMapping("/alon/reports/top-articles")
    public List<Map<String, Object>> topArticles(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return queries.topArticles(from, to);
    }

    /**
     * Expone compras agrupadas por artículo y proveedor con unidades e importes mediante la API,
     * delegando la consulta al repositorio.
     */
    @GetMapping("/alon/reports/purchases-by-article")
    public List<Map<String, Object>> purchasesByArticle(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return queries.purchasesByArticle(from, to);
    }

    /**
     * Expone proveedores del comercio con sus saldos pendientes mediante la API, delegando la
     * consulta al repositorio.
     */
    @GetMapping("/alon/reports/supplier-balances")
    public List<Map<String, Object>> supplierBalances() {
        return queries.supplierBalances();
    }

    /**
     * Expone gastos del comercio agrupados por día y categoría mediante la API, delegando la
     * consulta al repositorio.
     */
    @GetMapping("/alon/reports/expenses")
    public List<Map<String, Object>> expenses(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return queries.expenses(from, to);
    }

    /**
     * Expone artículos cuyo stock está por debajo del mínimo configurado mediante la API, delegando
     * la consulta al repositorio.
     */
    @GetMapping("/alon/reports/low-stock")
    public List<Map<String, Object>> lowStock() {
        return queries.lowStock();
    }
}
