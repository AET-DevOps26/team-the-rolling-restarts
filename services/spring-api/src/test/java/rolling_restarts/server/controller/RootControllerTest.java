package rolling_restarts.server.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RootControllerTest {
    // default constructor not needed

    @Test
    void rootReturnsHelloWorld() {
        RootController controller = new RootController();
        var response = controller.root();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("Hello, World!", body.getMessage());
    }
}
