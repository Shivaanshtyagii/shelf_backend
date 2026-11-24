package com.shelf.api.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
    @GetMapping("/api/health")
    HealthResponse health() { return new HealthResponse("up"); }

    record HealthResponse(String status) {}
}
