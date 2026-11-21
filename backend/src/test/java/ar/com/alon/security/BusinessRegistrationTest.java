package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.users.UserService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class BusinessRegistrationTest {
    @Test
    void aRetryCannotSilentlyChangeThePasswordEmailOrLicense(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        var passwords = new PasswordHasher();
        var crypto = new TotpSecurity(directory.resolve("key").toString());
        var original = input();
        String fingerprint =
                passwords.hash(
                        crypto.hash(
                                tools.jackson.databind.json.JsonMapper.builder()
                                        .build()
                                        .writeValueAsString(original)));
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(original.requestId())))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        7L,
                                        "nombre",
                                        "Comercio A",
                                        "alta_gerente",
                                        "gerente",
                                        "alta_huella",
                                        fingerprint)));
        var registry = mock(PlatformBusinesses.class);
        var registration = new BusinessRegistration(jdbc, registry, passwords, crypto);
        assertThat(registration.find(original)).isEqualTo(7L);
        for (var manager :
                List.of(
                        new BusinessRegistrationController.Manager(
                                "gerente", "Encargado", "5678", "gerente@example.test", 1),
                        new BusinessRegistrationController.Manager(
                                "gerente", "Encargado", "1234", "otro@example.test", 1),
                        new BusinessRegistrationController.Manager(
                                "gerente", "Encargado", "1234", "gerente@example.test", 2))) {
            assertThatThrownBy(
                            () ->
                                    registration.reserve(
                                            new BusinessRegistrationController.Confirmation(
                                                    original.requestId(), business(), manager)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("datos originales");
        }
        verifyNoInteractions(registry);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void registryOnlyCreationIsNotAnHttpEndpoint() throws Exception {
        assertThat(
                        PlatformBusinesses.class
                                .getMethod("create", PlatformBusinesses.Business.class)
                                .getAnnotation(
                                        org.springframework.web.bind.annotation.PostMapping.class))
                .isNull();
    }

    private PlatformBusinesses.Business business() {
        return new PlatformBusinesses.Business(
                "Comercio A",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "Inicial",
                java.math.BigDecimal.ZERO,
                "PRUEBA",
                java.time.LocalDate.of(2026, 11, 14),
                null,
                "",
                "",
                "",
                null,
                "",
                null);
    }

    private BusinessRegistrationController.Confirmation input() {
        return new BusinessRegistrationController.Confirmation(
                "abc13ed1-0000-4000-8000-012345678901",
                business(),
                new BusinessRegistrationController.Manager(
                        "gerente", "Encargado", "1234", "gerente@example.test", 1));
    }

    private RequestIdentity identity(String role) {
        return new RequestIdentity() {
            @Override
            public Map<String, Object> user() {
                return Map.of("rol", role);
            }
        };
    }

    @Test
    void createsAFullBusinessAdministratorAndOnlyReportsVerifiedSuccess() {
        var registration = mock(BusinessRegistration.class);
        var provisioning = mock(BusinessProvisioning.class);
        when(registration.reserve(any())).thenReturn(7L);
        when(provisioning.provision(eq(7L), any()))
                .thenReturn(
                        Map.of("running", true, "managementUrl", "/gestion", "shopUrl", "/tienda"));
        var response =
                new BusinessRegistrationController(registration, provisioning, identity("SUPER"))
                        .confirm(input());
        assertThat(response.getBody()).containsEntry("id", 7L).containsEntry("running", true);
        var setup = ArgumentCaptor.forClass(BusinessProvisioning.Input.class);
        verify(provisioning).provision(eq(7L), setup.capture());
        assertThat(setup.getValue().permissions())
                .containsExactlyInAnyOrderElementsOf(UserService.ALL_PERMISSIONS)
                .doesNotContain("administracion", "SUPER");
        assertThat(setup.getValue().username()).isEqualTo("gerente");
        var order = inOrder(provisioning, registration);
        order.verify(provisioning).validateProvision(any());
        order.verify(registration).reserve(any());
        order.verify(provisioning).provision(eq(7L), any());
    }

    @Test
    void preservesTheReservationAndReturnsAnErrorWhenProvisioningFails() {
        var registration = mock(BusinessRegistration.class);
        var provisioning = mock(BusinessProvisioning.class);
        when(registration.reserve(any())).thenReturn(7L);
        when(provisioning.provision(eq(7L), any()))
                .thenThrow(
                        new ResponseStatusException(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "MySQL sin permisos"));
        var response =
                new BusinessRegistrationController(registration, provisioning, identity("SUPER"))
                        .confirm(input());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody())
                .containsEntry("id", 7L)
                .containsEntry("registered", true)
                .containsEntry("running", false);
        assertThat(response.getBody().get("error").toString())
                .contains("MySQL sin permisos", "no se duplicará");
    }

    @Test
    void rejectsManagersBeforeAnyRegistrationOrProvisioning() {
        var registration = mock(BusinessRegistration.class);
        var provisioning = mock(BusinessProvisioning.class);
        assertThatThrownBy(
                        () ->
                                new BusinessRegistrationController(
                                                registration, provisioning, identity("DUENO"))
                                        .confirm(input()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(registration, provisioning);
    }

    @Test
    void requiresTheManagerBeforeAcceptingConfirmation() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var invalid =
                    new BusinessRegistrationController.Confirmation(
                            input().requestId(), business(), null);
            assertThat(factory.getValidator().validate(invalid))
                    .anyMatch(v -> v.getPropertyPath().toString().equals("manager"));
        }
    }

    @Test
    void retriesReuseTheSameBusinessAndDoNotPersistThePassword() {
        var passwords = mock(PasswordHasher.class);
        when(passwords.hash(anyString())).thenReturn("protected-fingerprint");
        when(passwords.verify(anyString(), eq("protected-fingerprint"))).thenReturn(true);
        var crypto = mock(TotpSecurity.class);
        when(crypto.hash(anyString())).thenReturn("digest");
        var jdbc = mock(JdbcTemplate.class);
        var registry = mock(PlatformBusinesses.class);
        when(jdbc.queryForList(
                        "SELECT id,nombre,alta_gerente,alta_huella,baja_iniciada_en FROM plataforma_negocios WHERE alta_token=?",
                        input().requestId()))
                .thenReturn(List.of())
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        7L,
                                        "nombre",
                                        "Comercio A",
                                        "alta_gerente",
                                        "gerente",
                                        "alta_huella",
                                        "protected-fingerprint")));
        when(registry.create(any())).thenReturn(Map.of("id", 7L));
        var service = new BusinessRegistration(jdbc, registry, passwords, crypto);
        assertThat(service.reserve(input())).isEqualTo(7L);
        assertThat(service.reserve(input())).isEqualTo(7L);
        verify(registry, times(1)).create(any());
        verify(jdbc)
                .update(
                        "UPDATE plataforma_negocios SET alta_token=?,alta_gerente=?,alta_huella=? WHERE id=?",
                        input().requestId(),
                        "gerente",
                        "protected-fingerprint",
                        7L);
    }
}
