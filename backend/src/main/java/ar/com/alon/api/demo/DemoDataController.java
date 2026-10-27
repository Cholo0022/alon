package ar.com.alon.api.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import ar.com.alon.model.DemoDataModel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoDataController {
    private final DemoDataModel model;

    public DemoDataController(DemoDataModel model) { this.model = model; }

    @GetMapping("/summary")
    public Map<String, Object> summary() { return model.summary(); }

    @GetMapping("/articles")
    public List<Map<String, Object>> articles(HttpServletRequest request, @RequestParam(defaultValue = "false") boolean inactive) {
        return model.articles((String) request.getAttribute("alonRole"), inactive);
    }

    @GetMapping("/stock-movements")
    public List<Map<String, Object>> stockMovements() { return model.stockMovements(); }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() { return model.categories(); }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers() { return model.customers(); }

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers() { return model.suppliers(); }

    @GetMapping("/store-orders")
    public List<Map<String, Object>> storeOrders() { return model.storeOrders(); }

    @GetMapping("/cash/status")
    public Map<String, Object> cashStatus(@RequestParam LocalDate date) { return model.cashStatus(date); }

    @GetMapping("/sales")
    public List<Map<String, Object>> sales(@RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return model.sales(date, page, size);
    }

    @GetMapping("/purchases")
    public List<Map<String, Object>> purchases() { return model.purchases(); }

    @GetMapping("/reports/daily-sales")
    public List<Map<String, Object>> dailySales() { return model.dailySales(); }

    @GetMapping("/reports/monthly-sales")
    public List<Map<String, Object>> monthlySales() { return model.monthlySales(); }

    @GetMapping("/reports/payment-methods")
    public List<Map<String, Object>> paymentMethods() { return model.paymentMethods(); }

    @GetMapping("/reports/top-customers")
    public List<Map<String, Object>> topCustomers() { return model.topCustomers(); }

    @GetMapping("/reports/top-articles")
    public List<Map<String, Object>> topArticles() { return model.topArticles(); }

    @GetMapping("/reports/purchases-by-article")
    public List<Map<String, Object>> purchasesByArticle() { return model.purchasesByArticle(); }

    @GetMapping("/reports/supplier-balances")
    public List<Map<String, Object>> supplierBalances() { return model.supplierBalances(); }

    @GetMapping("/reports/cash")
    public List<Map<String, Object>> cash() { return model.cash(); }

    @GetMapping("/reports/expenses")
    public List<Map<String, Object>> expenses() { return model.expenses(); }

    @GetMapping("/reports/low-stock")
    public List<Map<String, Object>> lowStock() { return model.lowStock(); }
}
