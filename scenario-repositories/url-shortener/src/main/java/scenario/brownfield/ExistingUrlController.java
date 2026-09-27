package scenario.brownfield;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExistingUrlController {
    private final ExistingUrlService service;
    public ExistingUrlController(ExistingUrlService service) { this.service = service; }
    @PostMapping("/legacy/urls") ResponseEntity<Void> create(@RequestBody LegacyCreate request) {
        service.save(request.code(), URI.create(request.target()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @GetMapping("/legacy/{code}") ResponseEntity<Void> redirect(@PathVariable String code) {
        return service.find(code).map(uri -> ResponseEntity.status(HttpStatus.FOUND).location(uri).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    record LegacyCreate(String code, String target) {}
}
