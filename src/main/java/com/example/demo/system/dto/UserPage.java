package com.example.demo.system.dto;

import java.util.List;

public record UserPage(List<UserResponse> items, int page, int pageSize, long total, long totalPages) {
}
