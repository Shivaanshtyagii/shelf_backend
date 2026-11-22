package com.shelf.listings.internal;

import com.shelf.accounts.Accounts;
import com.shelf.common.ApiFault;
import com.shelf.listings.Listing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class MarketplaceController {
    private final Accounts accounts;
    private final Marketplace marketplace;

    MarketplaceController(Accounts accounts, Marketplace marketplace) {
        this.accounts = accounts;
        this.marketplace = marketplace;
    }

    @GetMapping("/explore")
    Marketplace.Page explore(@RequestParam(defaultValue = "") @Size(max = 120) String q,
                             @RequestParam(defaultValue = "") @Size(max = 80) String subject,
                             @RequestParam(defaultValue = "RELEVANCE") ListingRepository.Sort sort,
                             @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                             @RequestParam(defaultValue = "12") @Min(1) @Max(40) int size) {
        return marketplace.discover(Listing.Scope.EXPLORE, null, new ListingRepository.Search(q, subject, sort, page, size));
    }

    @GetMapping("/campus")
    Marketplace.Page campus(Principal principal, @RequestParam Listing.Scope scope,
                             @RequestParam(defaultValue = "") @Size(max = 120) String q,
                             @RequestParam(defaultValue = "") @Size(max = 80) String subject,
                             @RequestParam(defaultValue = "NEWEST") ListingRepository.Sort sort,
                             @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                             @RequestParam(defaultValue = "12") @Min(1) @Max(40) int size) {
        if (scope == Listing.Scope.EXPLORE) throw ApiFault.badRequest("Choose a campus category.");
        return marketplace.discover(scope, accounts.verified(principal), new ListingRepository.Search(q, subject, sort, page, size));
    }

    @GetMapping("/listings/mine")
    List<Listing> mine(Principal principal) { return marketplace.mine(accounts.current(principal).id()); }

    @GetMapping("/listings/{id}")
    Listing owned(Principal principal, @PathVariable UUID id) {
        return marketplace.owned(id, accounts.current(principal).id());
    }

    @PostMapping("/listings")
    Listing draft(Principal principal, @Valid @RequestBody Draft input) {
        return marketplace.save(accounts.verified(principal).id(), null, input.toDraft());
    }

    @PutMapping("/listings/{id}")
    Listing edit(Principal principal, @PathVariable UUID id, @Valid @RequestBody Draft input) {
        return marketplace.save(accounts.verified(principal).id(), id, input.toDraft());
    }

    @PostMapping("/listings/{id}/submit")
    Listing submit(Principal principal, @PathVariable UUID id, @Valid @RequestBody Revision input) {
        return marketplace.submit(id, accounts.verified(principal).id(), input.revision());
    }

    @PostMapping("/listings/{id}/archive")
    Listing archive(Principal principal, @PathVariable UUID id) {
        return marketplace.archive(id, accounts.verified(principal).id());
    }

    @GetMapping("/listings/{id}/attachment")
    ResponseEntity<Void> attachment(Principal principal, @PathVariable UUID id) {
        var link = marketplace.attachment(id, accounts.verified(principal));
        return ResponseEntity.status(302).location(link).cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/seller")
    Map<String, String> seller(Principal principal) {
        return Map.of("status", marketplace.sellerStatus(accounts.current(principal).id()));
    }

    @PostMapping("/seller")
    Map<String, String> apply(Principal principal) {
        var member = accounts.verified(principal);
        marketplace.applyToSell(member.id());
        return Map.of("status", marketplace.sellerStatus(member.id()));
    }

    record Draft(@NotNull Listing.Scope scope, @NotBlank @Size(max = 120) String title,
                 @NotBlank @Size(max = 2000) String description, @NotBlank @Size(max = 80) String subject,
                 @Min(0) @Max(10_000_000) int priceMinor, UUID assetId, @Min(0) int revision) {
        Marketplace.Draft toDraft() {
            return new Marketplace.Draft(scope, title, description, subject, priceMinor, assetId, revision);
        }
    }
    record Revision(@Min(1) int revision) {}
}
