package com.vasanth.agenticsdlcorchestrator.validation;

import com.vasanth.agenticsdlcorchestrator.config.ValidationProperties;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.BuildEvidence;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.FailureClassification;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.MavenCapability;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FixedMavenCapabilityTool {
    private final ValidationProperties properties;

    public FixedMavenCapabilityTool(ValidationProperties properties) { this.properties = properties; }

    public BuildEvidence execute(Path workspace, MavenCapability capability) {
        ProcessBuilder builder = new ProcessBuilder(command(workspace, capability)).directory(workspace.toFile());
        sanitize(builder.environment());
        Instant started = Instant.now();
        try {
            Process process = builder.start();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.inputReader(StandardCharsets.UTF_8)));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.errorReader(StandardCharsets.UTF_8)));
            boolean finished = process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) process.destroyForcibly().waitFor();
            return evidence(capability, finished ? process.exitValue() : -1,
                    Duration.between(started, Instant.now()), !finished, stdout.join(), stderr.join());
        } catch (IOException exception) {
            return evidence(capability, -1, Duration.between(started, Instant.now()), false, "", exception.toString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return evidence(capability, -1, Duration.between(started, Instant.now()), true, "", "validation interrupted");
        }
    }

    protected List<String> command(Path workspace, MavenCapability capability) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path wrapper = workspace.resolve(windows ? "mvnw.cmd" : "mvnw");
        List<String> command = new ArrayList<>();
        if (windows) { command.add("cmd.exe"); command.add("/d"); command.add("/c"); }
        else { command.add("sh"); }
        command.add(wrapper.toAbsolutePath().toString());
        command.add("--batch-mode");
        command.add("--no-transfer-progress");
        command.add("clean");
        command.add(capability == MavenCapability.CLEAN_VERIFY ? "verify" : "test");
        return List.copyOf(command);
    }

    protected void sanitize(Map<String, String> environment) {
        environment.keySet().removeIf(key -> {
            String upper = key.toUpperCase(Locale.ROOT);
            return upper.contains("OPENAI") || upper.contains("API_KEY") || upper.contains("TOKEN")
                    || upper.contains("SECRET") || upper.contains("PASSWORD");
        });
    }

    private BuildEvidence evidence(MavenCapability capability, int exit, Duration duration, boolean timedOut,
                                   String stdout, String stderr) {
        stdout = bounded(stdout);
        stderr = bounded(stderr);
        String combined = (stdout + "\n" + stderr).toLowerCase(Locale.ROOT);
        FailureClassification classification = classify(exit, timedOut, combined);
        return new BuildEvidence(capability, exit, duration, timedOut, stdout, stderr, classification,
                extractCount(combined, "tests run:"), extractCount(combined, "failures:"),
                combined.contains("jacoco") ? "JaCoCo report generated" : "unavailable");
    }

    private FailureClassification classify(int exit, boolean timedOut, String output) {
        if (timedOut) return FailureClassification.TIMEOUT;
        if (exit == 0) return FailureClassification.NONE;
        if (output.contains("compilation failure") || output.contains("compiler error")) return FailureClassification.COMPILER;
        if (output.contains("there are test failures") || output.contains("failures:")) return FailureClassification.TEST;
        if (output.contains("could not resolve") || output.contains("failed to read artifact")) return FailureClassification.DEPENDENCY;
        if (output.contains("non-resolvable") || output.contains("malformed pom")) return FailureClassification.CONFIGURATION;
        if (output.contains("cannot find") || output.contains("no such file")) return FailureClassification.INFRASTRUCTURE;
        return FailureClassification.UNKNOWN;
    }

    private int extractCount(String output, String marker) {
        int total = 0, from = 0;
        while ((from = output.indexOf(marker, from)) >= 0) {
            int start = from + marker.length();
            while (start < output.length() && Character.isWhitespace(output.charAt(start))) start++;
            int end = start;
            while (end < output.length() && Character.isDigit(output.charAt(end))) end++;
            if (end > start) total = Integer.parseInt(output.substring(start, end));
            from = Math.max(end, from + marker.length());
        }
        return total;
    }

    private String read(java.io.BufferedReader reader) {
        try (reader) { return reader.lines().reduce("", (a, b) -> bounded(a + b + "\n")); }
        catch (IOException exception) { return exception.toString(); }
    }

    private String bounded(String value) {
        int max = properties.maxOutputCharacters();
        return value.length() <= max ? value : value.substring(0, max) + "\n[output truncated]";
    }
}
