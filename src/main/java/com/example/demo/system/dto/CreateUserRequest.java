package com.example.demo.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String account,
        @NotBlank @Size(max = 100) String passwd) {
}
