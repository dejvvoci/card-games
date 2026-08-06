package com.pesekatesh.controller;

import com.pesekatesh.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint i lehtë "jam gjallë" — përdoret nga keep-alive ping-u (shih .github/workflows/keep-alive.yml).
 * Bën edhe një pyetje minimale te baza e të dhënave (count()), jo vetëm kontroll i backend-it — kështu
 * ofruesi i DB-së (p.sh. Supabase) e sheh si aktivitet real dhe nuk e "pauzon" bazën për mungesë përdorimi.
 */
@RestController
public class HealthController {

    private final UserRepository userRepository;

    public HealthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/health")
    public String health() {
        userRepository.count();
        return "OK";
    }
}
