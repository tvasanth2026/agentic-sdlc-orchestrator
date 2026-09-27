package com.vasanth.agenticsdlcorchestrator.patch;

import com.vasanth.agenticsdlcorchestrator.config.ModelProviderProperties;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelRequest;
import com.vasanth.agenticsdlcorchestrator.model.BoundedModelGateway;
import com.vasanth.agenticsdlcorchestrator.model.ModelBoundaryException;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AgentPatchProposal;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperation;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperationType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class ModelFileOperationProposalAgent implements FileOperationProposalAgent {
    private final BoundedModelGateway gateway;
    private final ModelProviderProperties properties;
    private final ObjectMapper objectMapper;

    public ModelFileOperationProposalAgent(BoundedModelGateway gateway, ModelProviderProperties properties,
                                           ObjectMapper objectMapper) {
        this.gateway = gateway; this.properties = properties; this.objectMapper = objectMapper;
    }

    @Override
    public List<AgentPatchProposal> propose(ProposalContext context) {
        List<String> hashes = List.of(context.requirementHash(), context.repositoryAnalysisHash(), context.planHash());
        List<FileOperation> production = new ArrayList<>();
        String serviceSource = serviceContent();
        if (context.normalizedRequirement().toLowerCase(java.util.Locale.ROOT).contains("repair scenario"))
            serviceSource = serviceSource.replace("@Service public class", "BROKEN_TOKEN @Service public class");
        production.add(operation("src/main/java/agentic/generated/url/GeneratedUrlService.java", serviceSource,
                "Implement alias reservation, expiry, redirect and UTC analytics.", context, "implementation-service", hashes));
        production.add(operation("src/main/java/agentic/generated/url/GeneratedUrlController.java", controllerContent(),
                "Connect requested behavior to HTTP runtime routes.", context, "implementation-api", hashes));
        if (!Files.exists(Path.of(context.workspaceLocation()).resolve("src/main/java"))) {
            production.add(operation("src/main/java/agentic/generated/url/GeneratedUrlApplication.java", applicationContent(),
                    "Provide the greenfield application entry point.", context, "implementation-bootstrap", hashes));
        }
        List<FileOperation> tests = List.of(
                operation("src/test/java/agentic/generated/url/GeneratedUrlServiceTest.java", serviceTestContent(),
                        "Prove aliases, conflicts, validation, expiry and UTC analytics.", context, "test-service", hashes),
                operation("src/test/java/agentic/generated/url/GeneratedUrlHttpTest.java", httpTestContent(),
                        "Prove create and redirect through the actual HTTP controller.", context, "test-http", hashes));
        return List.of(invoke("IMPLEMENTATION", production, context), invoke("TEST_GENERATION", tests, context));
    }

    @Override
    public Optional<AgentPatchProposal> proposeRepair(RepairProposalContext context) {
        if (properties.provider().equalsIgnoreCase("deterministic")) return deterministicRepair(context);
        Map<String, Object> modelContext = new LinkedHashMap<>();
        modelContext.put("objective", "Correct the real compiler or test failure without unrelated changes");
        modelContext.put("failureEvidence", context.boundedFailureEvidence());
        modelContext.put("relevantSources", context.relevantSources());
        modelContext.put("priorProposal", context.priorProposalJson());
        var response = gateway.generateRaw(new ModelRequest("REPAIR", "file_operation_proposal",
                "Return only a corrected structured patch. UPDATE and DELETE must use current SHA-256 values.",
                modelContext, properties.maxOutputCharacters()));
        GeneratedOperations generated = deserialize(response.structuredOutput());
        return generated.operations().isEmpty() ? Optional.empty() : Optional.of(new AgentPatchProposal(
                UUID.randomUUID(), "REPAIR", response.provider(), response.model(), generated.operations()));
    }

    private Optional<AgentPatchProposal> deterministicRepair(RepairProposalContext context) {
        String source = context.relevantSources();
        int marker = source.indexOf("BROKEN_TOKEN");
        if (marker < 0) return Optional.empty();
        int pathStart = source.lastIndexOf("PATH: ", marker);
        int hashStart = source.indexOf("SHA256: ", pathStart);
        int contentStart = source.indexOf('\n', hashStart) + 1;
        int nextPath = source.indexOf("\nPATH: ", marker);
        String path = source.substring(pathStart + 6, source.indexOf('\n', pathStart)).trim();
        String hash = source.substring(hashStart + 8, source.indexOf('\n', hashStart)).trim();
        String content = source.substring(contentStart, nextPath < 0 ? source.length() : nextPath + 1)
                .replace("BROKEN_TOKEN ", "");
        FileOperation operation = new FileOperation(path, FileOperationType.UPDATE, content, hash,
                "Remove the token identified by the real compiler failure.", context.requirementId(),
                context.acceptanceCriterionIds(), "repair-compiler-failure", context.inputArtifactHashes());
        return Optional.of(new AgentPatchProposal(UUID.randomUUID(), "REPAIR", "deterministic", "deterministic-v1",
                List.of(operation)));
    }

    private FileOperation operation(String path, String content, String reason, ProposalContext context,
                                    String task, List<String> hashes) {
        return new FileOperation(path, FileOperationType.CREATE, content, null, reason, context.requirementId(),
                context.acceptanceCriterionIds(), task, hashes);
    }

    private AgentPatchProposal invoke(String role, List<FileOperation> proposed, ProposalContext context) {
        Map<String, Object> modelContext = new LinkedHashMap<>();
        modelContext.put("objective", context.normalizedRequirement());
        modelContext.put("proposedOperations", new GeneratedOperations(proposed));
        var response = gateway.generateRaw(new ModelRequest(role, "file_operation_proposal",
                "Return complete requirement-specific structured file operations, not prose.",
                modelContext, properties.maxOutputCharacters()));
        return new AgentPatchProposal(UUID.randomUUID(), role, response.provider(), response.model(),
                deserialize(response.structuredOutput()).operations());
    }

    private GeneratedOperations deserialize(String json) {
        try { return objectMapper.readValue(json, GeneratedOperations.class); }
        catch (JacksonException exception) { throw new ModelBoundaryException("invalid file-operation proposal", exception); }
    }

    private String serviceContent() { return """
            package agentic.generated.url;
            import java.net.URI;
            import java.security.SecureRandom;
            import java.time.*;
            import java.util.*;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicLong;
            import org.springframework.stereotype.Service;
            @Service public class GeneratedUrlService {
                private final Map<String, Link> links = new ConcurrentHashMap<>();
                private final Map<String, Map<LocalDate, AtomicLong>> daily = new ConcurrentHashMap<>();
                private final Clock clock;
                private final SecureRandom random;
                private final Set<String> blockedHosts;
                public GeneratedUrlService() { this(Clock.systemUTC(), new SecureRandom(), Set.of()); }
                GeneratedUrlService(Clock clock) { this(clock, new SecureRandom(), Set.of()); }
                GeneratedUrlService(Clock clock, SecureRandom random, Set<String> blockedHosts) {
                    this.clock = clock; this.random = random; this.blockedHosts = Set.copyOf(blockedHosts);
                }
                public Link create(String target, String requestedAlias, Instant expiresAt) {
                    URI uri = validateTarget(target);
                    if (expiresAt != null && !expiresAt.isAfter(clock.instant()))
                        throw new IllegalArgumentException("expiry must be in the future");
                    String alias = requestedAlias == null || requestedAlias.isBlank()
                            ? reserveGeneratedCode("us") : requestedAlias;
                    if (!alias.matches("[A-Za-z0-9_-]{4,32}")) throw new IllegalArgumentException("invalid alias");
                    Link link = new Link(alias, uri, expiresAt, true, clock.instant());
                    if (links.putIfAbsent(alias, link) != null) throw new AliasConflictException(alias);
                    return link;
                }
                public Optional<URI> redirect(String alias) {
                    Link link = links.get(alias);
                    if (link == null) return Optional.empty();
                    if (!link.active()) throw new InactiveLinkException(alias);
                    if (link.expiresAt() != null && !link.expiresAt().isAfter(clock.instant())) throw new ExpiredLinkException(alias);
                    LocalDate day = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
                    daily.computeIfAbsent(alias, ignored -> new ConcurrentHashMap<>())
                            .computeIfAbsent(day, ignored -> new AtomicLong()).incrementAndGet();
                    return Optional.of(link.target());
                }
                public Analytics analytics(String alias) {
                    Map<LocalDate, AtomicLong> values = daily.getOrDefault(alias, Map.of());
                    Map<LocalDate, Long> counts = values.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey, entry -> entry.getValue().get()));
                    return new Analytics(counts.values().stream().mapToLong(Long::longValue).sum(), counts);
                }
                public Optional<Link> inspect(String alias) { return Optional.ofNullable(links.get(alias)); }
                public boolean deactivate(String alias) {
                    return links.computeIfPresent(alias, (key, link) -> new Link(key, link.target(), link.expiresAt(), false, link.createdAt())) != null;
                }
                public int cleanup(Duration retention) {
                    Instant cutoff = clock.instant().minus(retention);
                    int before = links.size();
                    links.entrySet().removeIf(entry -> (!entry.getValue().active()
                            || (entry.getValue().expiresAt() != null && !entry.getValue().expiresAt().isAfter(clock.instant())))
                            && entry.getValue().createdAt().isBefore(cutoff));
                    daily.keySet().retainAll(links.keySet());
                    return before - links.size();
                }
                private URI validateTarget(String target) {
                    URI uri;
                    try { uri = URI.create(target); } catch (RuntimeException ex) { throw new IllegalArgumentException("malformed target URL"); }
                    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                    if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null)
                        throw new IllegalArgumentException("target must be an absolute HTTP URL");
                    if (uri.getUserInfo() != null) throw new IllegalArgumentException("URL user information is forbidden");
                    String host = uri.getHost().toLowerCase(Locale.ROOT);
                    if (blockedHosts.contains(host) || isPrivateHost(host)) throw new IllegalArgumentException("target host is blocked");
                    return uri;
                }
                private boolean isPrivateHost(String host) {
                    if (host.equals("localhost") || host.equals("::1") || host.startsWith("127.") || host.startsWith("10.")
                            || host.startsWith("192.168.") || host.equals("0.0.0.0")) return true;
                    if (host.startsWith("172.")) {
                        try { int second = Integer.parseInt(host.split("\\\\.")[1]); return second >= 16 && second <= 31; }
                        catch (RuntimeException ignored) { return true; }
                    }
                    return false;
                }
                private String reserveGeneratedCode(String region) {
                    for (int attempt = 0; attempt < 10; attempt++) {
                        String value = region + "_" + String.format("%08x", random.nextInt());
                        if (!links.containsKey(value)) return value;
                    }
                    throw new IllegalStateException("code collision retry limit exceeded");
                }
                public record Link(String alias, URI target, Instant expiresAt, boolean active, Instant createdAt) {}
                public record Analytics(long total, Map<LocalDate, Long> utcDaily) {}
                public static class AliasConflictException extends RuntimeException { public AliasConflictException(String value) { super(value); } }
                public static class ExpiredLinkException extends RuntimeException { public ExpiredLinkException(String value) { super(value); } }
                public static class InactiveLinkException extends RuntimeException { public InactiveLinkException(String value) { super(value); } }
            }
            """; }

    private String controllerContent() { return """
            package agentic.generated.url;
            import java.net.URI;
            import java.time.Instant;
            import java.time.Duration;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            import org.springframework.http.*;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.web.server.ResponseStatusException;
            import org.springframework.scheduling.annotation.Scheduled;
            @RestController public class GeneratedUrlController {
                private final GeneratedUrlService service;
                private final Map<String, AtomicInteger> redirectRequests = new ConcurrentHashMap<>();
                public GeneratedUrlController(GeneratedUrlService service) { this.service = service; }
                @PostMapping("/urls") ResponseEntity<Created> create(@RequestBody Create command) {
                    try { var link = service.create(command.url(), command.alias(), command.expiresAt());
                        return ResponseEntity.created(URI.create("/" + link.alias())).body(new Created(link.alias(), "/" + link.alias()));
                    } catch (GeneratedUrlService.AliasConflictException ex) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "alias already exists");
                    } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
                }
                @GetMapping("/{code}") ResponseEntity<Void> redirect(@PathVariable String code) {
                    if (redirectRequests.computeIfAbsent(code, ignored -> new AtomicInteger()).incrementAndGet() > 100)
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "60").build();
                    try { return service.redirect(code).map(uri -> ResponseEntity.status(HttpStatus.FOUND).location(uri).<Void>build())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    } catch (GeneratedUrlService.ExpiredLinkException | GeneratedUrlService.InactiveLinkException ex) {
                        throw new ResponseStatusException(HttpStatus.GONE);
                    }
                }
                @GetMapping("/urls/{code}") GeneratedUrlService.Link inspect(@PathVariable String code) {
                    return service.inspect(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                }
                @DeleteMapping("/urls/{code}") ResponseEntity<Void> deactivate(@PathVariable String code) {
                    return service.deactivate(code) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
                }
                @GetMapping("/urls/{code}/analytics") GeneratedUrlService.Analytics analytics(@PathVariable String code) {
                    return service.analytics(code);
                }
                @GetMapping("/v3/api-docs") Map<String, Object> openApi() {
                    return Map.of("openapi", "3.1.0", "info", Map.of("title", "Generated URL Shortener", "version", "1.0.0"),
                            "paths", Map.of("/urls", Map.of("post", Map.of()), "/{code}", Map.of("get", Map.of())));
                }
                @Scheduled(cron = "0 0 * * * *") void cleanup() { service.cleanup(Duration.ofDays(30)); }
                @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
                    ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(),
                            ex.getReason() == null ? "request failed" : ex.getReason());
                    detail.setTitle("URL shortener request failed");
                    return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
                }
                record Create(String url, String alias, Instant expiresAt) {}
                record Created(String code, String shortUrl) {}
            }
            """; }

    private String applicationContent() { return """
            package agentic.generated.url;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.scheduling.annotation.EnableScheduling;
            @EnableScheduling
            @SpringBootApplication public class GeneratedUrlApplication {
                public static void main(String[] args) { SpringApplication.run(GeneratedUrlApplication.class, args); }
            }
            """; }

    private String serviceTestContent() { return """
            package agentic.generated.url;
            import static org.assertj.core.api.Assertions.*;
            import java.time.*;
            import org.junit.jupiter.api.Test;
            class GeneratedUrlServiceTest {
                private final Instant now = Instant.parse("2026-01-02T12:00:00Z");
                private final MutableClock clock = new MutableClock(now);
                private final GeneratedUrlService service = new GeneratedUrlService(clock);
                @Test void reservesRedirectsAndCountsUtcDay() {
                    service.create("https://example.com/a", "Alias_1", null);
                    assertThat(service.redirect("Alias_1")).contains(java.net.URI.create("https://example.com/a"));
                    assertThat(service.analytics("Alias_1").total()).isEqualTo(1);
                    assertThat(service.analytics("Alias_1").utcDaily()).containsEntry(LocalDate.parse("2026-01-02"), 1L);
                }
                @Test void rejectsDuplicateInvalidExpiredAndProvesCaseSensitivity() {
                    service.create("https://example.com", "caseName", null);
                    assertThatThrownBy(() -> service.create("https://other.example", "caseName", null)).isInstanceOf(GeneratedUrlService.AliasConflictException.class);
                    assertThatThrownBy(() -> service.create("https://example.com", "x!", null)).isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> service.create("https://example.com/old", "PastExpiry", now.minusSeconds(1)))
                            .isInstanceOf(IllegalArgumentException.class);
                    service.create("https://example.com/old", "Expired1", now.plusSeconds(1));
                    clock.advance(Duration.ofSeconds(2));
                    assertThatThrownBy(() -> service.redirect("Expired1")).isInstanceOf(GeneratedUrlService.ExpiredLinkException.class);
                    assertThat(service.redirect("CASENAME")).isEmpty();
                }
                @Test void blocksUnsafeDestinationsAndSupportsInspectionDeactivationAndRegionalCodes() {
                    assertThatThrownBy(() -> service.create("ftp://example.com", null, null)).isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> service.create("https://user:pass@example.com", null, null)).isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> service.create("http://127.0.0.1/admin", null, null)).isInstanceOf(IllegalArgumentException.class);
                    var generated = service.create("https://example.com/safe", null, null);
                    assertThat(generated.alias()).startsWith("us_");
                    assertThat(service.inspect(generated.alias())).isPresent();
                    assertThat(service.deactivate(generated.alias())).isTrue();
                    assertThatThrownBy(() -> service.redirect(generated.alias())).isInstanceOf(GeneratedUrlService.InactiveLinkException.class);
                }
                @Test void supportsConcurrentCreationWithoutLostLinks() throws Exception {
                    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                        var futures = java.util.stream.IntStream.range(0, 25)
                                .mapToObj(index -> executor.submit(() -> service.create("https://example.com/" + index, "Code" + index, null)))
                                .toList();
                        for (var future : futures) assertThat(future.get()).isNotNull();
                    }
                }
                private static final class MutableClock extends Clock {
                    private Instant current;
                    private MutableClock(Instant current) { this.current = current; }
                    void advance(Duration duration) { current = current.plus(duration); }
                    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                    @Override public Clock withZone(ZoneId zone) { return this; }
                    @Override public Instant instant() { return current; }
                }
            }
            """; }

    private String httpTestContent() { return """
            package agentic.generated.url;
            import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
            import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
            import org.junit.jupiter.api.Test;
            import org.springframework.http.MediaType;
            import org.springframework.test.web.servlet.MockMvc;
            import org.springframework.test.web.servlet.setup.MockMvcBuilders;
            class GeneratedUrlHttpTest {
                @Test void createsRedirectsAndRejectsDuplicateThroughRuntime() throws Exception {
                    MockMvc mvc = MockMvcBuilders.standaloneSetup(
                            new GeneratedUrlController(new GeneratedUrlService())).build();
                    mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON)
                            .content("{\\\"url\\\":\\\"https://example.com/path\\\",\\\"alias\\\":\\\"demoAlias\\\"}"))
                            .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("demoAlias"));
                    mvc.perform(get("/demoAlias")).andExpect(status().isFound())
                            .andExpect(header().string("Location", "https://example.com/path"));
                    mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON)
                            .content("{\\\"url\\\":\\\"https://other.example\\\",\\\"alias\\\":\\\"demoAlias\\\"}"))
                            .andExpect(status().isConflict());
                    mvc.perform(get("/urls/demoAlias")).andExpect(status().isOk())
                            .andExpect(jsonPath("$.active").value(true));
                    mvc.perform(delete("/urls/demoAlias")).andExpect(status().isNoContent());
                    mvc.perform(get("/demoAlias")).andExpect(status().isGone())
                            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
                    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                            .andExpect(jsonPath("$.openapi").value("3.1.0"));
                }
                @Test void rejectsUnsafeUrlAndReturnsRetryAfterWhenRateLimited() throws Exception {
                    MockMvc mvc = MockMvcBuilders.standaloneSetup(
                            new GeneratedUrlController(new GeneratedUrlService())).build();
                    mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON)
                            .content("{\\\"url\\\":\\\"http://localhost/admin\\\",\\\"alias\\\":\\\"Unsafe1\\\"}"))
                            .andExpect(status().isBadRequest())
                            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
                    mvc.perform(post("/urls").contentType(MediaType.APPLICATION_JSON)
                            .content("{\\\"url\\\":\\\"https://example.com\\\",\\\"alias\\\":\\\"RateCode\\\"}"))
                            .andExpect(status().isCreated());
                    for (int index = 0; index < 100; index++) mvc.perform(get("/RateCode")).andExpect(status().isFound());
                    mvc.perform(get("/RateCode")).andExpect(status().isTooManyRequests())
                            .andExpect(header().string("Retry-After", "60"));
                }
            }
            """; }

    private record GeneratedOperations(List<FileOperation> operations) {
        private GeneratedOperations { operations = List.copyOf(operations); }
    }
}
