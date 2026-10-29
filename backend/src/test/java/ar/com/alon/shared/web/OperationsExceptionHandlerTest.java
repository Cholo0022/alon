package ar.com.alon.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class OperationsExceptionHandlerTest {
    @RestController
    static class Probe {
        @GetMapping("/probe")
        String get(@RequestParam String required) {
            return required;
        }
    }

    @Test
    void preservesClientErrorStatusesInsteadOfReturningServerErrors() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new Probe())
                        .setControllerAdvice(new OperationsExceptionHandler())
                        .build();
        assertThat(mvc.perform(post("/probe")).andReturn().getResponse().getStatus())
                .isEqualTo(405);
        assertThat(mvc.perform(get("/probe")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(
                        mvc.perform(get("/probe").param("required", "ok"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
    }
}
