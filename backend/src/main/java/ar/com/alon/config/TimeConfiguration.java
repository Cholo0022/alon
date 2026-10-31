// Configura el reloj de Alon con la zona horaria del comercio.
package ar.com.alon.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    /**
     * Proporciona un reloj con la zona de Argentina para que las fechas del negocio no dependan de
     * la configuración del servidor.
     */
    @Bean
    public Clock businessClock() {
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
