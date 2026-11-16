package ar.com.alon.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstanceWebControllerTest {
    @TempDir Path temporary;

    @Test
    void servesBothNamedLinksAndKeepsExistingLinksWorking() throws Exception {
        Files.writeString(temporary.resolve("index.html"), "<html>Alon</html>");
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new InstanceWebController(temporary.toString(), "las-tres-l"))
                        .build();
        for (String path :
                new String[] {
                    "/", "/tienda", "/las-tres-l", "/tienda/las-tres-l", "/tienda/las-tres-l/"
                })
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string("<html>Alon</html>"));
        mvc.perform(get("/otro-negocio")).andExpect(status().isNotFound());
        mvc.perform(get("/tienda/otro-negocio")).andExpect(status().isNotFound());
    }
}
