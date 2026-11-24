package com.shelf.api.listings.web;

import com.shelf.api.listings.service.SellerService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/seller")
class SellerController {
    private final SellerService sellers;

    SellerController(SellerService sellers) {
        this.sellers = sellers;
    }

    @GetMapping
    SellerStatusResponse seller(Principal principal) {
        return new SellerStatusResponse(sellers.sellerStatus(principal));
    }

    @PostMapping
    SellerStatusResponse apply(Principal principal) {
        return new SellerStatusResponse(sellers.applyToSell(principal));
    }
    record SellerStatusResponse(String status) {}
}
