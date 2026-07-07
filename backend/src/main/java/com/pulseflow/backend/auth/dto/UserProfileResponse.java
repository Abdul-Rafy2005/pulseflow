package com.pulseflow.backend.auth.dto;

import java.time.LocalDateTime;

import com.pulseflow.backend.auth.UserRole;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        UserRole role,
        LocalDateTime createdAt
) {
}

