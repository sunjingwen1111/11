package com.example.demo.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String account,
        @Size(max = 100) String passwd) {
}
