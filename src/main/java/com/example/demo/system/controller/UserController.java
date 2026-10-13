package com.example.demo.system.controller;

import com.example.demo.system.dto.CreateUserRequest;
import com.example.demo.system.dto.UpdateUserRequest;
import com.example.demo.system.dto.UserPage;
import com.example.demo.system.dto.UserResponse;
import com.example.demo.system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public UserPage list(@RequestParam(defaultValue = "1") int page) {
        return service.list(page);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable int id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        service.delete(id);
    }
}
