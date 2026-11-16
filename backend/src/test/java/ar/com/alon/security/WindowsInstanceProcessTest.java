package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class WindowsInstanceProcessTest {
    @Test
    void transportsTheExactScriptWithoutChangingQuotesOrAccents() {
        String script = "$text = \"Comercio de Andrés\"; Write-Output $text";
        var command = WindowsInstanceProcess.command(script).command();
        assertThat(command).contains("-EncodedCommand").doesNotContain("-Command");
        assertThat(
                        new String(
                                Base64.getDecoder().decode(command.getLast()),
                                StandardCharsets.UTF_16LE))
                .isEqualTo(script);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void preservesTheQuotedWindowsProcessFilterInARealPowerShellInvocation() throws Exception {
        // No consulta ni detiene procesos: verifica el transporte de la expresión que antes
        // fallaba.
        String script =
                """
            $ErrorActionPreference = 'Stop'
            $ProgressPreference = 'SilentlyContinue'
            function Test-Filter { param([string]$Filter)
              if ($Filter -ne "Name='java.exe' OR Name='javaw.exe'") { throw 'FILTER_CHANGED' }
              Write-Output 'FILTER_OK'
            }
            Test-Filter -Filter "Name='java.exe' OR Name='javaw.exe'"
            """;
        var process = WindowsInstanceProcess.command(script).redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes());
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("FILTER_OK").doesNotContain("FILTER_CHANGED");
        } finally {
            if (process.isAlive()) process.destroy();
        }
    }
}
