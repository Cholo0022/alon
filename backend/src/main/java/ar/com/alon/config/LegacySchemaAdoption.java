// Permite adoptar una base antigua solo cuando se solicita y su estructura está completa.
package ar.com.alon.config;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Solo adopta una instalación anterior cuando se solicita y su estructura está completa. */
@Configuration
@ConditionalOnProperty(name = "alon.adopt-legacy-schema", havingValue = "true")
public class LegacySchemaAdoption {
    /**
     * Configura la adopción explícita de una base anterior: valida estructura e integridad antes de
     * registrar la base y ejecutar Flyway.
     */
    @Bean
    public FlywayMigrationStrategy adoptLegacySchema() {
        return flyway -> {
            try (Connection connection =
                    flyway.getConfiguration().getDataSource().getConnection()) {
                var metadata = connection.getMetaData();
                try (var history =
                        metadata.getTables(
                                connection.getCatalog(), null, "flyway_schema_history", null)) {
                    if (!history.next()) {
                        Map<String, List<String>> required =
                                Map.of(
                                        "usuarios", List.of("password_hash", "rol", "permisos"),
                                        "articulos",
                                                List.of(
                                                        "flete_unitario",
                                                        "flete_porcentaje",
                                                        "margen_porcentaje",
                                                        "redondeo_precio"),
                                        "cajas", List.of("numero_sesion", "sesion_abierta"),
                                        "ventas", List.of("fecha_hora"),
                                        "proveedores", List.of("cuit"),
                                        "pedidos_tienda", List.of("total_estimado"),
                                        "movimientos_stock", List.of("cantidad"));
                        for (var table : required.entrySet()) {
                            for (String column : table.getValue()) {
                                try (var found =
                                        metadata.getColumns(
                                                connection.getCatalog(),
                                                null,
                                                table.getKey(),
                                                column)) {
                                    if (!found.next())
                                        throw new IllegalStateException(
                                                "Falta completar las migraciones antiguas: "
                                                        + table.getKey()
                                                        + "."
                                                        + column);
                                }
                            }
                        }
                        try (var statement = connection.createStatement();
                                var duplicates =
                                        statement.executeQuery(
                                                "SELECT comercio_id,LOWER(TRIM(nombre)) FROM rubros GROUP BY comercio_id,LOWER(TRIM(nombre)) HAVING COUNT(*)>1 LIMIT 1")) {
                            if (duplicates.next())
                                throw new IllegalStateException(
                                        "Hay rubros con nombres duplicados. Corregilos antes de adoptar el esquema.");
                        }
                        flyway.baseline();
                    }
                }
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(
                        "No se pudo verificar el esquema anterior.", exception);
            }
            flyway.migrate();
        };
    }
}
