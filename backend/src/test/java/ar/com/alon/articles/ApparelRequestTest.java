package ar.com.alon.articles;

import static org.assertj.core.api.Assertions.*;

import ar.com.alon.articles.dto.ArticleInput;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class ApparelRequestTest {
    @Test
    void acceptsTheCompleteStrictApparelRequest() {
        var mapper =
                JsonMapper.builder()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                        .build();
        var input =
                mapper.readValue(
                        """
  {"code":"Z38N","name":"Urbano · 38 · Negro","categoryId":0,"supplierId":1,"unit":"UNIDAD","cost":100,"freightPerUnit":0,"freightPercent":10,"freightByValue":false,"freightByPercent":true,"marginPercent":25,"roundPrice":true,"initialStock":2,"minimumStock":0,"publishOnWeb":false,"photos":[],"apparel":{"kind":"CALZADO","model":"Urbano","brand":"Marca","size":"38","sizeSystem":"AR","color":"Negro","material":"Cuero","audience":"UNISEX","season":"TODO EL AÑO","description":"Calzado urbano"}}
  """,
                        ArticleInput.class);
        try (var validator = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(validator.getValidator().validate(input)).isEmpty();
        }
        assertThat(input.apparel().size()).isEqualTo("38");
        assertThat(
                        ArticlePricing.calculateSalePrice(
                                input.cost(), input.freightPercent(), input.marginPercent(), false))
                .isEqualByComparingTo("137.50");
    }
}
