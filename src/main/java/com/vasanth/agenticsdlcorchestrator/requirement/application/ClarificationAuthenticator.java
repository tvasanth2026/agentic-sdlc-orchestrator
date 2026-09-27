package com.vasanth.agenticsdlcorchestrator.requirement.application;

import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ClarificationAuthenticator {
    private final byte[] expected;

    public ClarificationAuthenticator(AgenticExecutionProperties properties) {
        this.expected = properties.clarificationToken().getBytes(StandardCharsets.UTF_8);
    }

    public void authenticate(String supplied) {
        byte[] candidate = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, candidate)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid operator token");
        }
    }
}
