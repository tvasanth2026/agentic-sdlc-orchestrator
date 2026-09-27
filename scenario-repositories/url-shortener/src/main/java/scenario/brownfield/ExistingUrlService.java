package scenario.brownfield;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ExistingUrlService {
    private final Map<String, URI> links = new ConcurrentHashMap<>();
    public void save(String code, URI target) { links.put(code, target); }
    public Optional<URI> find(String code) { return Optional.ofNullable(links.get(code)); }
}
