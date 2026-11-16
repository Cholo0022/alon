import java.nio.charset.StandardCharsets;
import java.util.Base64;
class PowerShellArgumentProbe {
 public static void main(String[] args) throws Exception {
  String script="""
   $ErrorActionPreference = 'Stop'
   function Test-Filter { param([string]$Filter)
    if ($Filter -ne "Name='java.exe' OR Name='javaw.exe'") { throw ('FILTER_CHANGED: ' + $Filter) }
    Write-Output 'FILTER_OK'
   }
   Test-Filter -Filter "Name='java.exe' OR Name='javaw.exe'"
   """;
  for(String mode:new String[]{"-Command","-EncodedCommand"}) {
   String body=mode.equals("-Command")?script:Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
   var process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden",mode,body).redirectErrorStream(true).start();
   String output=new String(process.getInputStream().readAllBytes());
   System.out.println(mode+" EXIT="+process.waitFor()+" "+output);
  }
 }
}
