package com.example.demo.system.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 100) String account,
        @NotBlank String passwd) {
}
