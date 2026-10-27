package ar.com.alon.api.operations;

import java.util.Map;
import java.time.LocalDate;
import ar.com.alon.model.OperationsModel;

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
    private final OperationsModel model;

    public OperationsController(OperationsModel model) { this.model = model; }

    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCustomer(@RequestBody OperationsModel.CustomerInput input) { return model.createCustomer(input); }

    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplier(@RequestBody OperationsModel.SupplierInput input) { return model.createSupplier(input); }

    @PostMapping("/articles") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createArticle(@RequestBody OperationsModel.ArticleInput input) { return model.createArticle(input); }

    @PutMapping("/articles/{id}")
    public Map<String, Object> updateArticle(@PathVariable long id, @RequestBody OperationsModel.ArticleInput input) { return model.updateArticle(id, input); }

    @PostMapping("/articles/{id}/deactivate")
    public Map<String, Object> deactivateArticle(@PathVariable long id) { return model.setArticleActive(id, false); }

    @PostMapping("/articles/{id}/activate")
    public Map<String, Object> activateArticle(@PathVariable long id) { return model.setArticleActive(id, true); }

    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCategory(@RequestBody OperationsModel.CategoryInput input) { return model.createCategory(input); }

    @PostMapping("/sales") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSale(@RequestBody OperationsModel.SaleInput input) { return model.createSale(input); }

    @PostMapping("/purchases") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPurchase(@RequestBody OperationsModel.PurchaseInput input) { return model.createPurchase(input); }

    @PostMapping("/suppliers/receipt") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSupplierReceipt(@RequestBody OperationsModel.SupplierReceiptInput input) { return model.createSupplierReceipt(input); }

    @PostMapping("/cash/open") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> openCash(@RequestBody OperationsModel.CashOpenInput input) { return model.openCash(input); }

    @PostMapping("/cash/expenses") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addExpense(@RequestBody OperationsModel.ExpenseInput input) { return model.addExpense(input); }

    @PostMapping("/cash/close")
    public Map<String, Object> closeCash(@RequestBody OperationsModel.CashCloseInput input) { return model.closeCash(input); }

    @GetMapping("/cash/summary")
    public Map<String, Object> cashSummary(@RequestParam LocalDate date) { return model.cashSummary(date); }

    @PostMapping("/store-orders") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createStoreOrder(@RequestBody OperationsModel.StoreOrderInput input) { return model.createStoreOrder(input); }
}
