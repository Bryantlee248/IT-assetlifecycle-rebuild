package com.itam.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
