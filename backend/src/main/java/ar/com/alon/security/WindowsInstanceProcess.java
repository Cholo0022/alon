package ar.com.alon.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Fallback para Windows cuando ProcessHandle no entrega los argumentos de Java. */
final class WindowsInstanceProcess {
    private WindowsInstanceProcess() {}

    static Optional<ProcessHandle> find(Path executable) throws IOException {
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"))
            return Optional.empty();
        // El argumento del negocio viaja por entorno, nunca como código PowerShell concatenado.
        String script =
                """
            $ErrorActionPreference = 'Stop'
            $ProgressPreference = 'SilentlyContinue'
            $target = [IO.Path]::GetFullPath($env:ALON_EXPECTED_INSTANCE_JAR)
            $escaped = [regex]::Escape($target)
            $pattern = '(?:^|\\s)-jar\\s+(?:"' + $escaped + '"|' + $escaped + ')\\s*$'
            Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop |
              Where-Object { $_.CommandLine -and $_.CommandLine -imatch $pattern } |
              ForEach-Object { [string]$_.ProcessId }
            """;
        var builder = command(script);
        builder.environment()
                .put(
                        "ALON_EXPECTED_INSTANCE_JAR",
                        executable.toAbsolutePath().normalize().toString());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        var process = builder.start();
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroy();
                throw new IOException("Windows no respondió al verificar la instancia.");
            }
            if (process.exitValue() != 0)
                throw new IOException("Windows no permitió verificar el proceso del negocio.");
            try (var reader = process.inputReader()) {
                for (String line : reader.lines().limit(20).toList()) {
                    if (!line.trim().matches("[1-9][0-9]{0,9}")) continue;
                    var handle = ProcessHandle.of(Long.parseLong(line.trim()));
                    if (handle.isPresent() && handle.get().isAlive()) return handle;
                }
            }
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la verificación del proceso.", exception);
        }
    }

    /** Evita que el análisis de argumentos de Windows quite las comillas del comando. */
    static ProcessBuilder command(String script) {
        return new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle",
                "Hidden",
                "-EncodedCommand",
                Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)));
    }
}
