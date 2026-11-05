package ar.com.alon.sales;

import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alon/sales")
public class SalesReceiptController {
    private final SalesQueries queries;

    public SalesReceiptController(SalesQueries queries) {
        this.queries = queries;
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable long id) throws IOException {
        var data = queries.receipt(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=venta-" + id + ".pdf")
                .cacheControl(CacheControl.noStore())
                .body(SalesReceiptPdf.create(data));
    }
}
