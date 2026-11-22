package com.shelf.listings.internal.controller;

import com.shelf.accounts.Accounts;
import com.shelf.listings.internal.service.SellerService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/seller")
class SellerController {
    private final Accounts accounts;
    private final SellerService sellers;

    SellerController(Accounts accounts, SellerService sellers) {
        this.accounts = accounts;
        this.sellers = sellers;
    }

    @GetMapping
    Map<String, String> seller(Principal principal) {
        return Map.of("status", sellers.sellerStatus(accounts.current(principal).id()));
    }

    @PostMapping
    Map<String, String> apply(Principal principal) {
        var member = accounts.verified(principal);
        sellers.applyToSell(member.id());
        return Map.of("status", sellers.sellerStatus(member.id()));
    }
}
