// Inicia el servidor de Alon y carga los componentes de la aplicación.
package ar.com.alon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AlonApplication {
    /** Inicia Spring Boot y registra los componentes que atienden la API de gestión y la tienda. */
    public static void main(String[] args) {
        SpringApplication.run(AlonApplication.class, args);
    }
}
