// Recibe las solicitudes de informes del negocio y las deriva al servicio correspondiente.
package ar.com.alon.reporting;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportQueries queries;

    public ReportController(ReportQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/alon/reports/daily-sales")
    public List<Map<String, Object>> dailySales() {
        return queries.dailySales();
    }

    @GetMapping("/alon/reports/monthly-sales")
    public List<Map<String, Object>> monthlySales() {
        return queries.monthlySales();
    }

    @GetMapping("/alon/reports/payment-methods")
    public List<Map<String, Object>> paymentMethods() {
        return queries.paymentMethods();
    }

    @GetMapping("/alon/reports/top-customers")
    public List<Map<String, Object>> topCustomers() {
        return queries.topCustomers();
    }

    @GetMapping("/alon/reports/top-articles")
    public List<Map<String, Object>> topArticles() {
        return queries.topArticles();
    }

    @GetMapping("/alon/reports/purchases-by-article")
    public List<Map<String, Object>> purchasesByArticle() {
        return queries.purchasesByArticle();
    }

    @GetMapping("/alon/reports/supplier-balances")
    public List<Map<String, Object>> supplierBalances() {
        return queries.supplierBalances();
    }

    @GetMapping("/alon/reports/expenses")
    public List<Map<String, Object>> expenses() {
        return queries.expenses();
    }

    @GetMapping("/alon/reports/low-stock")
    public List<Map<String, Object>> lowStock() {
        return queries.lowStock();
    }
}
