package ar.com.alon.backup;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/backups")
public class BackupController {
    private final BackupService service;
    private final BackupRestoreService restore;

    public BackupController(BackupService service, BackupRestoreService restore) {
        this.service = service;
        this.restore = restore;
    }

    @GetMapping
    public List<BackupService.Backup> list() throws IOException {
        return service.list();
    }

    @PostMapping
    public BackupService.Backup create() {
        return service.create();
    }

    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BackupRestoreService.Result restore(
            @RequestPart("file") MultipartFile file,
            @RequestParam String confirmation,
            HttpServletRequest request)
            throws IOException {
        if (file.isEmpty() || file.getSize() > 512L * 1024 * 1024)
            throw new IllegalArgumentException("Seleccioná un ZIP de backup de hasta 512 MB.");
        try (var input = file.getInputStream()) {
            var result = restore.restore(input, confirmation);
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            return result;
        }
    }

    @GetMapping("/{name}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String name)
            throws IOException {
        var file = service.download(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(java.nio.file.Files.size(file))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .body(new FileSystemResource(file));
    }
}
