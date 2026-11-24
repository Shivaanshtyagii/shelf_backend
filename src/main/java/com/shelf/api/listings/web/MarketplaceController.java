package com.shelf.api.listings.web;

import com.shelf.api.listings.api.Listing;
import com.shelf.api.listings.api.ListingPageResponse;
import com.shelf.api.listings.api.ListingResponse;
import com.shelf.api.listings.domain.ListingDraft;
import com.shelf.api.listings.domain.ListingSearch;
import com.shelf.api.listings.service.ListingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class MarketplaceController {
    private final ListingService marketplace;

    MarketplaceController(ListingService marketplace) {
        this.marketplace = marketplace;
    }

    @GetMapping("/explore")
    ListingPageResponse explore(@RequestParam(defaultValue = "") @Size(max = 120) String q,
                             @RequestParam(defaultValue = "") @Size(max = 80) String subject,
                             @RequestParam(defaultValue = "RELEVANCE") ListingSearch.Sort sort,
                             @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                             @RequestParam(defaultValue = "12") @Min(1) @Max(40) int size) {
        return marketplace.explore(new ListingSearch(q, subject, sort, page, size));
    }

    @GetMapping("/campus")
    ListingPageResponse campus(Principal principal, @RequestParam Listing.Scope scope,
                             @RequestParam(defaultValue = "") @Size(max = 120) String q,
                             @RequestParam(defaultValue = "") @Size(max = 80) String subject,
                             @RequestParam(defaultValue = "NEWEST") ListingSearch.Sort sort,
                             @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                             @RequestParam(defaultValue = "12") @Min(1) @Max(40) int size) {
        return marketplace.campus(scope, principal, new ListingSearch(q, subject, sort, page, size));
    }

    @GetMapping("/listings/mine")
    List<ListingResponse> mine(Principal principal) {
        return marketplace.mine(principal).stream().map(ListingResponse::from).toList();
    }

    @GetMapping("/listings/{id}")
    ListingResponse owned(Principal principal, @PathVariable UUID id) {
        return ListingResponse.from(marketplace.myListing(id, principal));
    }

    @PostMapping("/listings")
    ListingResponse draft(Principal principal, @Valid @RequestBody ListingDraftRequest input) {
        return ListingResponse.from(marketplace.save(principal, null, input.toDraft()));
    }

    @PutMapping("/listings/{id}")
    ListingResponse edit(Principal principal, @PathVariable UUID id, @Valid @RequestBody ListingDraftRequest input) {
        return ListingResponse.from(marketplace.save(principal, id, input.toDraft()));
    }

    @PostMapping("/listings/{id}/submit")
    ListingResponse submit(Principal principal, @PathVariable UUID id, @Valid @RequestBody RevisionRequest input) {
        return ListingResponse.from(marketplace.submit(id, principal, input.revision()));
    }

    @PostMapping("/listings/{id}/archive")
    ListingResponse archive(Principal principal, @PathVariable UUID id) {
        return ListingResponse.from(marketplace.archive(id, principal));
    }

    @GetMapping("/listings/{id}/attachment")
    ResponseEntity<Void> attachment(Principal principal, @PathVariable UUID id) {
        var link = marketplace.attachment(id, principal);
        return ResponseEntity.status(302).location(link).cacheControl(CacheControl.noStore()).build();
    }

    record ListingDraftRequest(@NotNull Listing.Scope scope, @NotBlank @Size(max = 120) String title,
                 @NotBlank @Size(max = 2000) String description, @NotBlank @Size(max = 80) String subject,
                 @Min(0) @Max(10_000_000) int priceMinor, UUID assetId, @Min(0) int revision) {
        ListingDraft toDraft() {
            return new ListingDraft(scope, title, description, subject, priceMinor, assetId, revision);
        }
    }
    record RevisionRequest(@Min(1) int revision) {}
}
