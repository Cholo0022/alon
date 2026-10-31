// Configura el reloj de Alon con la zona horaria del comercio.
package ar.com.alon.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    @Bean
    public Clock businessClock() {
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
