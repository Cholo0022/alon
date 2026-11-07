package ar.com.alon.storefront;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import ar.com.alon.security.BrowserSecurityFilter;
import ar.com.alon.security.LoginThrottle;
import ar.com.alon.shared.web.OperationsExceptionHandler;
import ar.com.alon.storefront.dto.GuestCheckoutInput;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GuestCheckoutHttpTest {
    private final ShopService shop = mock(ShopService.class);
    private MockMvc mvc;
    private static final String BODY =
            """
        {"buyer":{"firstName":"Ana","lastName":"Pérez","phone":"3446236311","dni":"30111222","province":"Entre Ríos","city":"Larroque"},"lines":[{"articleId":1,"quantity":1,"expectedPrice":100}]}
        """;

    @BeforeEach
    void setup() {
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new ShopController(shop, new LoginThrottle(Clock.systemUTC())))
                        .setControllerAdvice(new OperationsExceptionHandler())
                        .addFilters(new BrowserSecurityFilter())
                        .build();
    }

    @Test
    void acceptsAPublicOrderWithoutCredentialsButWithCsrfAndAnOperationKey() throws Exception {
        when(shop.checkoutGuest(any(GuestCheckoutInput.class), eq("guest-order-123456")))
                .thenReturn(Map.of("id", 42, "estado", "PENDIENTE_CONFIRMACION"));
        var request = new MockHttpServletRequest();
        String csrf = BrowserSecurityFilter.token(request);
        var session = (MockHttpSession) request.getSession();
        var response =
                mvc.perform(
                                post("/api/v1/shop/orders")
                                        .session(session)
                                        .header("X-CSRF-Token", csrf)
                                        .header("Idempotency-Key", "guest-order-123456")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BODY))
                        .andReturn()
                        .getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        verify(shop).checkoutGuest(any(GuestCheckoutInput.class), eq("guest-order-123456"));
        assertThat(session.getAttribute("alonShopper")).isNull();
    }

    @Test
    void rejectsInvalidPhonesAndNonNumericDniBeforeCallingTheService() throws Exception {
        for (String body :
                new String[] {
                    BODY.replace("3446236311", "344623631"),
                    BODY.replace("3446236311", "5493446236311"),
                    BODY.replace("3446236311", "3446abcdef"),
                    BODY.replace("30111222", "30.111.222")
                }) {
            var request = new MockHttpServletRequest();
            String csrf = BrowserSecurityFilter.token(request);
            var response =
                    mvc.perform(
                                    post("/api/v1/shop/orders")
                                            .session((MockHttpSession) request.getSession())
                                            .header("X-CSRF-Token", csrf)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))
                            .andReturn()
                            .getResponse();
            assertThat(response.getStatus()).isEqualTo(400);
        }
        verifyNoInteractions(shop);
    }

    @Test
    void stillRequiresCsrfForAnonymousOrders() throws Exception {
        assertThat(
                        mvc.perform(
                                        post("/api/v1/shop/orders")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(BODY))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        verifyNoInteractions(shop);
    }

    @Test
    void doesNotExposeOldAccountEndpointsOrPublicOrderHistory() throws Exception {
        for (String path : new String[] {"/login", "/register", "/password", "/password/reset"}) {
            var request = new MockHttpServletRequest();
            String csrf = BrowserSecurityFilter.token(request);
            assertThat(
                            mvc.perform(
                                            post("/api/v1/shop" + path)
                                                    .session((MockHttpSession) request.getSession())
                                                    .header("X-CSRF-Token", csrf)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content("{}"))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus())
                    .isEqualTo(410);
        }
        assertThat(mvc.perform(get("/api/v1/shop/orders")).andReturn().getResponse().getStatus())
                .isEqualTo(410);
        verifyNoInteractions(shop);
    }

    @Test
    void anonymousSessionDoesNotDiscloseOldShopperData() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute("alonShopper", Map.of("id", 1, "dni", "30111222"));
        session.setAttribute("alonShopperCredential", "old-version");
        var response =
                mvc.perform(get("/api/v1/shop/session").session(session)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString())
                .contains("csrfToken")
                .doesNotContain("30111222", "user", "password");
        assertThat(session.getAttribute("alonShopper")).isNull();
        assertThat(session.getAttribute("alonShopperCredential")).isNull();
    }
}
