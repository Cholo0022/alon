package ar.com.alon.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Cada instancia sirve su propia copia del frontend desde el mismo origen que la API. */
@Configuration
@ConditionalOnProperty(name = "alon.instance-web", havingValue = "true")
public class InstanceWebConfiguration implements WebMvcConfigurer {
    private final String directory;

    public InstanceWebConfiguration(@Value("${alon.web-directory:./web}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations(directory);
    }
}
