package com.shelf.commerce.internal.controller;

import com.shelf.accounts.Accounts;
import com.shelf.commerce.internal.model.CommerceOverview;
import com.shelf.commerce.internal.service.DigitalCommerceService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/commerce")
class DigitalCommerceController {
    private final Accounts accounts;
    private final DigitalCommerceService commerce;

    DigitalCommerceController(Accounts accounts, DigitalCommerceService commerce) {
        this.accounts = accounts;
        this.commerce = commerce;
    }

    @GetMapping
    CommerceOverview overview(Principal principal) {
        accounts.current(principal);
        return commerce.overview();
    }

    @PostMapping({"/checkout", "/refunds", "/payouts"})
    void disabled(Principal principal) {
        accounts.verified(principal);
        commerce.requireAvailable();
    }

}
