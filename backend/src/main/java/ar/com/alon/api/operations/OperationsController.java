package ar.com.alon.api.operations;

import java.util.Map;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService service;

    public OperationsController(OperationsService service) { this.service = service; }

    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCustomer(@RequestBody OperationsService.CustomerInput input) { return service.createCustomer(input); }

    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplier(@RequestBody OperationsService.SupplierInput input) { return service.createSupplier(input); }

    @PostMapping("/articles") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createArticle(@RequestBody OperationsService.ArticleInput input) { return service.createArticle(input); }

    @PutMapping("/articles/{id}")
    public Map<String, Object> updateArticle(@PathVariable long id, @RequestBody OperationsService.ArticleInput input) { return service.updateArticle(id, input); }

    @PostMapping("/articles/{id}/deactivate")
    public Map<String, Object> deactivateArticle(@PathVariable long id) { return service.setArticleActive(id, false); }

    @PostMapping("/articles/{id}/activate")
    public Map<String, Object> activateArticle(@PathVariable long id) { return service.setArticleActive(id, true); }

    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCategory(@RequestBody OperationsService.CategoryInput input) { return service.createCategory(input); }

    @PostMapping("/sales") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSale(@RequestBody OperationsService.SaleInput input) { return service.createSale(input); }

    @PostMapping("/purchases") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPurchase(@RequestBody OperationsService.PurchaseInput input) { return service.createPurchase(input); }

    @PostMapping("/suppliers/receipt") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplierReceipt(@RequestBody OperationsService.SupplierReceiptInput input) { return service.createSupplierReceipt(input); }

    @PostMapping("/cash/open") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> openCash(@RequestBody OperationsService.CashOpenInput input) { return service.openCash(input); }

    @PostMapping("/cash/expenses") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addExpense(@RequestBody OperationsService.ExpenseInput input) { return service.addExpense(input); }

    @PostMapping("/cash/close")
    public Map<String, Object> closeCash(@RequestBody OperationsService.CashCloseInput input) { return service.closeCash(input); }

    @GetMapping("/cash/summary")
    public Map<String, Object> cashSummary(@RequestParam LocalDate date) { return service.cashSummary(date); }

    @PostMapping("/store-orders") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createStoreOrder(@RequestBody OperationsService.StoreOrderInput input) { return service.createStoreOrder(input); }
}
