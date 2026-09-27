package com.vasanth.agenticsdlcorchestrator.repository.analysis;

import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryFile;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryMap;
import com.vasanth.agenticsdlcorchestrator.repository.tool.ControlledRepositoryTools;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AcceptanceCriterion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepositoryAnalyzer {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern MAPPING = Pattern.compile("@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\\(\\\"([^\\\"]*)\\\"[^)]*\\))?");
    private final ControlledRepositoryTools tools;

    public RepositoryAnalyzer(ControlledRepositoryTools tools) {
        this.tools = tools;
    }

    public RepositoryMap analyze(List<AcceptanceCriterion> criteria) {
        List<RepositoryFile> files = tools.listFiles(".");
        Set<String> modules = new LinkedHashSet<>();
        Set<String> packages = new LinkedHashSet<>();
        Set<String> apis = new LinkedHashSet<>();
        List<String> controllers = new ArrayList<>();
        List<String> services = new ArrayList<>();
        List<String> domain = new ArrayList<>();
        List<String> repositories = new ArrayList<>();
        List<String> persistence = new ArrayList<>();
        List<String> migrations = new ArrayList<>();
        List<String> tests = new ArrayList<>();
        Set<String> conventions = new LinkedHashSet<>();
        Map<String, String> contents = new LinkedHashMap<>();

        for (RepositoryFile file : files) {
            String path = file.relativePath();
            String lowerPath = path.toLowerCase(Locale.ROOT);
            if (lowerPath.endsWith("pom.xml")) { modules.add(moduleOf(path)); conventions.add("Maven"); }
            if (lowerPath.endsWith("build.gradle") || lowerPath.endsWith("build.gradle.kts")) {
                modules.add(moduleOf(path)); conventions.add("Gradle");
            }
            if (lowerPath.contains("src/main/java") || lowerPath.endsWith(".kt")) {
                String content = tools.readFile(path);
                contents.put(path, content);
                Matcher packageMatcher = PACKAGE.matcher(content);
                if (packageMatcher.find()) packages.add(packageMatcher.group(1));
                Matcher mappingMatcher = MAPPING.matcher(content);
                while (mappingMatcher.find()) apis.add(mappingMatcher.group());
                classify(path, content, controllers, services, domain, repositories, persistence);
            }
            if (lowerPath.contains("db/migration") && lowerPath.endsWith(".sql")) migrations.add(path);
            if (lowerPath.contains("src/test") || lowerPath.endsWith("test.java") || lowerPath.endsWith("tests.kt")) tests.add(path);
            if (lowerPath.endsWith("application.yaml") || lowerPath.endsWith("application.yml")) conventions.add("Spring configuration");
        }

        Map<String, List<String>> impacts = new LinkedHashMap<>();
        for (AcceptanceCriterion criterion : criteria) {
            Set<String> terms = meaningfulTerms(criterion.description());
            List<String> matches = contents.entrySet().stream()
                    .filter(entry -> terms.stream().anyMatch(term -> entry.getKey().toLowerCase(Locale.ROOT).contains(term)
                            || entry.getValue().toLowerCase(Locale.ROOT).contains(term)))
                    .map(Map.Entry::getKey).limit(25).toList();
            impacts.put(criterion.id(), matches);
        }
        List<String> flows = inferFlows(controllers, services, repositories, persistence);
        return new RepositoryMap(new ArrayList<>(modules), new ArrayList<>(packages), new ArrayList<>(apis),
                controllers, services, domain, repositories, persistence, migrations, tests, flows,
                new ArrayList<>(conventions), impacts);
    }

    private void classify(String path, String content, List<String> controllers, List<String> services,
                          List<String> domain, List<String> repositories, List<String> persistence) {
        if (content.contains("@RestController") || content.contains("@Controller")) controllers.add(path);
        if (content.contains("@Service") || path.endsWith("Service.java")) services.add(path);
        if (content.contains("@Entity") || path.contains("/domain/") || path.contains("\\domain\\")) domain.add(path);
        if (content.contains("JpaRepository") || content.contains("CrudRepository") || path.endsWith("Repository.java")) repositories.add(path);
        if (content.contains("@Entity") || content.contains("JdbcTemplate") || content.contains("JpaRepository")) persistence.add(path);
    }

    private List<String> inferFlows(List<String> controllers, List<String> services,
                                    List<String> repositories, List<String> persistence) {
        List<String> flows = new ArrayList<>();
        if (!controllers.isEmpty() && !services.isEmpty()) flows.add("HTTP controller -> application service");
        if (!services.isEmpty() && !repositories.isEmpty()) flows.add("application service -> repository");
        if (!repositories.isEmpty() && !persistence.isEmpty()) flows.add("repository -> persistence store");
        return flows;
    }

    private Set<String> meaningfulTerms(String value) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 5 && !Set.of("requested", "behavior", "through", "production", "automated", "tests").contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private String moduleOf(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "." : path.substring(0, index);
    }
}
