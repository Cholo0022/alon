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

    @GetMapping("/demo/reports/daily-sales")
    public List<Map<String, Object>> dailySales() {
        return queries.dailySales();
    }

    @GetMapping("/demo/reports/monthly-sales")
    public List<Map<String, Object>> monthlySales() {
        return queries.monthlySales();
    }

    @GetMapping("/demo/reports/payment-methods")
    public List<Map<String, Object>> paymentMethods() {
        return queries.paymentMethods();
    }

    @GetMapping("/demo/reports/top-customers")
    public List<Map<String, Object>> topCustomers() {
        return queries.topCustomers();
    }

    @GetMapping("/demo/reports/top-articles")
    public List<Map<String, Object>> topArticles() {
        return queries.topArticles();
    }

    @GetMapping("/demo/reports/purchases-by-article")
    public List<Map<String, Object>> purchasesByArticle() {
        return queries.purchasesByArticle();
    }

    @GetMapping("/demo/reports/supplier-balances")
    public List<Map<String, Object>> supplierBalances() {
        return queries.supplierBalances();
    }

    @GetMapping("/demo/reports/expenses")
    public List<Map<String, Object>> expenses() {
        return queries.expenses();
    }

    @GetMapping("/demo/reports/low-stock")
    public List<Map<String, Object>> lowStock() {
        return queries.lowStock();
    }
}
