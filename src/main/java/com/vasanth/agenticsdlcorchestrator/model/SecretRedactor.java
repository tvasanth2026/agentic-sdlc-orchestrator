package com.vasanth.agenticsdlcorchestrator.model;

import java.util.regex.Pattern;

public final class SecretRedactor {
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]{8,}");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*['\"]?[^\\s,'\"}]{4,}");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----[\\s\\S]*?-----END(?: [A-Z]+)? PRIVATE KEY-----");

    public String redact(String value) {
        if (value == null) return null;
        String result = PRIVATE_KEY.matcher(value).replaceAll("[REDACTED_PRIVATE_KEY]");
        result = BEARER.matcher(result).replaceAll("Bearer [REDACTED]");
        return ASSIGNMENT.matcher(result).replaceAll("$1=[REDACTED]");
    }
}
