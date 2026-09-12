package thorization;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test
    void testAppLoads() {
        assertDoesNotThrow(() -> {
            // Check App class existence
            assertNotNull(App.class);
        });
    }
}
