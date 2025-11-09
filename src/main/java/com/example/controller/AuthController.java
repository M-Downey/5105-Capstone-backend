package com.example.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.service.UserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private record RegisterReq(@NotBlank String username, @NotBlank String password, @JsonProperty("isAdmin") boolean isAdmin) {}
    private record LoginReq(@NotBlank String username, @NotBlank String password) {}
    private record LoginResp(String token) {}

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterReq req) {
        userService.register(req.username(), req.password(), req.isAdmin());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResp> login(@RequestBody LoginReq req) {
        String token = userService.login(req.username(), req.password());
        return ResponseEntity.ok(new LoginResp(token));
    }
}








