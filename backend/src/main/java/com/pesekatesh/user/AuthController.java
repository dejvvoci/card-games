package com.pesekatesh.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthTokenService tokenService;

    public AuthController(UserService userService, AuthTokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        try {
            User user = userService.register(req.getUsername(), req.getPassword());
            String token = tokenService.issueToken(user.getId());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody AuthRequest req) {
        return userService.authenticate(req.getUsername(), req.getPassword())
                .<ResponseEntity<Object>>map(user -> ResponseEntity.ok(new AuthResponse(tokenService.issueToken(user.getId()), user.getUsername())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Emri i përdoruesit ose fjalëkalimi është gabim."));
    }
}
