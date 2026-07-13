package com.pesekatesh.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint i lehtë "jam gjallë" — përdoret nga keep-alive ping-u (shih .github/workflows/keep-alive.yml) */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public String health() {
        return "OK";
    }
}
