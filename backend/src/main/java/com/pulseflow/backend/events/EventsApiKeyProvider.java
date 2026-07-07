package com.pulseflow.backend.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds and validates the API key used by client applications to POST events.
 * The key is injected from configuration — never hardcoded in source.
 * See PRD §9.2 and CLAUDE.md §5 for rationale.
 */
@Component
public class EventsApiKeyProvider {

    private final String apiKey;

    public EventsApiKeyProvider(@Value("${app.events.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Returns true if the supplied key matches the configured API key.
     */
    public boolean isValid(String suppliedKey) {
        if (suppliedKey == null || apiKey == null) {
            return false;
        }
        return apiKey.equals(suppliedKey);
    }
}
