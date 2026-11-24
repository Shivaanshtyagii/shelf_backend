package com.shelf.api.capabilities.service;

import com.shelf.api.assets.api.Assets;
import com.shelf.api.capabilities.api.CapabilitiesResponse;
import com.shelf.api.commerce.api.CommerceAvailability;
import org.springframework.stereotype.Service;

@Service
public class CapabilitiesService {
    private final Assets assets;
    private final CommerceAvailability commerce;

    public CapabilitiesService(Assets assets, CommerceAvailability commerce) {
        this.assets = assets;
        this.commerce = commerce;
    }

    public CapabilitiesResponse current() {
        return new CapabilitiesResponse(assets.configured(), commerce.enabled());
    }
}
