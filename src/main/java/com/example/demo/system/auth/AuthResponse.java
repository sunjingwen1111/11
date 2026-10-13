package com.example.demo.system.auth;

import com.example.demo.system.dto.UserResponse;

public record AuthResponse(String tokenName, String tokenValue, UserResponse user) {
}
