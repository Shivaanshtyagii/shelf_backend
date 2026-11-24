package com.shelf.api.commerce.web;

import com.shelf.api.commerce.api.CommerceOverviewResponse;
import com.shelf.api.commerce.service.DigitalCommerceService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/commerce")
class DigitalCommerceController {
    private final DigitalCommerceService commerce;

    DigitalCommerceController(DigitalCommerceService commerce) {
        this.commerce = commerce;
    }

    @GetMapping
    CommerceOverviewResponse overview(Principal principal) {
        return commerce.overview(principal);
    }

    @PostMapping({"/checkout", "/refunds", "/payouts"})
    void disabled(Principal principal) {
        commerce.requireAvailable(principal);
    }
}
