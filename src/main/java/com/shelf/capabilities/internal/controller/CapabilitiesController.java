package com.shelf.capabilities.internal.controller;

import com.shelf.assets.Assets;
import com.shelf.commerce.CommerceAvailability;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CapabilitiesController {
    private final Assets assets;
    private final CommerceAvailability commerce;

    CapabilitiesController(Assets assets, CommerceAvailability commerce) {
        this.assets = assets;
        this.commerce = commerce;
    }

    @GetMapping("/api/capabilities")
    Capabilities capabilities() {
        return new Capabilities(assets.configured(), commerce.enabled());
    }

    record Capabilities(boolean assets, boolean digitalCommerce) {}
}
