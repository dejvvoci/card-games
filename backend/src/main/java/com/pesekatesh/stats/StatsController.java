package com.pesekatesh.stats;

import com.pesekatesh.user.AuthTokenService;
import com.pesekatesh.user.User;
import com.pesekatesh.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final AuthTokenService tokenService;
    private final UserRepository userRepository;
    private final GameResultService gameResultService;

    public StatsController(AuthTokenService tokenService, UserRepository userRepository,
                            GameResultService gameResultService) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.gameResultService = gameResultService;
    }

    @GetMapping("/{gameType}")
    public ResponseEntity<?> getStats(@PathVariable String gameType,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractBearerToken(authHeader);
        Long userId = tokenService.resolveUserId(token).orElse(null);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Duhet të jesh i loguar.");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Llogaria nuk ekziston më.");
        }

        GameType type;
        try {
            type = GameType.valueOf(gameType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Lojë e panjohur: " + gameType);
        }
        return ResponseEntity.ok(gameResultService.getStats(user, type));
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring("Bearer ".length()).trim();
    }
}
