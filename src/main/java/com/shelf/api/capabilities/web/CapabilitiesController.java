package com.shelf.api.capabilities.web;

import com.shelf.api.capabilities.api.CapabilitiesResponse;
import com.shelf.api.capabilities.service.CapabilitiesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CapabilitiesController {
    private final CapabilitiesService service;

    CapabilitiesController(CapabilitiesService service) {
        this.service = service;
    }

    @GetMapping("/api/capabilities")
    CapabilitiesResponse capabilities() { return service.current(); }
}
