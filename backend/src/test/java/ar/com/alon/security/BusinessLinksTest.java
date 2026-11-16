package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusinessLinksTest {
    @Test
    void turnsCommercialNamesIntoSafePaths() {
        assertThat(BusinessLinks.slug("  Las Trés L & Hijos S.R.L.  "))
                .isEqualTo("las-tres-l-hijos-s-r-l");
        assertThat(BusinessLinks.slug("Tienda")).isEqualTo("negocio-tienda");
        assertThat(BusinessLinks.slug("***")).isEqualTo("negocio");
        assertThat(BusinessLinks.slug("á".repeat(200))).hasSize(70);
        assertThat(BusinessLinks.management(7, "las-tres-l"))
                .isEqualTo("http://127.0.0.1:20007/las-tres-l");
        assertThat(BusinessLinks.shop(7, "las-tres-l"))
                .isEqualTo("http://127.0.0.1:20007/tienda/las-tres-l");
        assertThat(BusinessLinks.management(8, "las-tres-l"))
                .isNotEqualTo(BusinessLinks.management(7, "las-tres-l"));
    }
}
