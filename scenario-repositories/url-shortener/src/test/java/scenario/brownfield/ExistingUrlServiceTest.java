package scenario.brownfield;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ExistingUrlServiceTest {
    @Test void storesExistingRedirect() {
        var service = new ExistingUrlService();
        service.save("legacy", URI.create("https://example.com"));
        assertThat(service.find("legacy")).contains(URI.create("https://example.com"));
    }
}
