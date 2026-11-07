package ar.com.alon.shared.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import ar.com.alon.articles.dto.PurchaseApparelInput;
import jakarta.validation.Valid;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ApparelRequestBindingTest {
    private static final String PAYLOAD =
            """
        {"product":{"code":"REM-S","name":"Remera","categoryId":0,"supplierId":0,"unit":"UNIDAD","cost":100,"freightByValue":false,"freightByPercent":true,"freightPerUnit":0,"freightPercent":0,"marginPercent":0,"minimumStock":0,"roundPrice":true,"publishOnWeb":true,"initialStock":0,"photos":[],"apparel":{"kind":"INDUMENTARIA","model":"Remera","brand":"Sin marca","size":"S","color":"Negro","sizeSystem":"LETRAS","material":"Algodón","audience":"UNISEX","season":"TODO EL AÑO","description":"Remera"}},"variants":[{"code":"REM-S","size":"S","color":"Negro","quantity":5}]}
        """;

    @RestController
    static class Probe {
        @PostMapping("/apparel-probe")
        Map<String, Boolean> save(@Valid @RequestBody PurchaseApparelInput input) {
            return Map.of("ok", true);
        }
    }

    @Test
    void acceptsTheCompleteFormPayload() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new Probe())
                        .setControllerAdvice(new OperationsExceptionHandler())
                        .build();
        String payload = PAYLOAD;

        var result =
                mvc.perform(
                                post("/apparel-probe")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                        .andReturn();
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    @Test
    void identifiesTheExactSizeQuantityWhenItsFormatIsInvalid() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new Probe())
                        .setControllerAdvice(new OperationsExceptionHandler())
                        .build();
        var response =
                mvc.perform(
                                post("/apparel-probe")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                PAYLOAD.replace(
                                                        "\"quantity\":5",
                                                        "\"quantity\":\"cinco\"")))
                        .andReturn()
                        .getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        var body =
                tools.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .readValue(
                                response.getContentAsString(
                                        java.nio.charset.StandardCharsets.UTF_8),
                                Map.class);
        assertThat(((Map<?, ?>) body.get("fields")).containsKey("variants[0].quantity")).isTrue();
        assertThat(body.get("error").toString()).contains("cantidad del renglón 1");
    }

    @Test
    void identifiesMissingCommonFields() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new Probe())
                        .setControllerAdvice(new OperationsExceptionHandler())
                        .build();
        var response =
                mvc.perform(
                                post("/apparel-probe")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                PAYLOAD.replace(
                                                        "\"name\":\"Remera\"", "\"name\":\"\"")))
                        .andReturn()
                        .getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        var body =
                tools.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .readValue(
                                response.getContentAsString(
                                        java.nio.charset.StandardCharsets.UTF_8),
                                Map.class);
        assertThat(((Map<?, ?>) body.get("fields")).containsKey("product.name")).isTrue();
    }
}
