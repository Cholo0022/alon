package ar.com.alon.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class AutomaticUserAccessTest {
    @Test
    void userCreationAlsoAuthorizesItsCurrentBrowserInTheSameRequest() {
        var model = mock(UserService.class);
        var security = mock(AccessSecurityService.class);
        var input =
                new UserService.UserInput(
                        "vendedor2", "Vendedor", "1234", "VENDEDOR", List.of("ventas"));
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        when(model.createUser(input)).thenReturn(Map.of("id", 2L));
        when(security.allowCreatedUser(2L, request, response)).thenReturn(true);
        var result =
                new AuthController(model, mock(LoginThrottle.class), security)
                        .createUser(input, request, response);
        assertThat(result).containsEntry("id", 2L).containsEntry("browserAllowed", true);
        var order = inOrder(model, security);
        order.verify(model).createUser(input);
        order.verify(security).allowCreatedUser(2L, request, response);
    }
}
