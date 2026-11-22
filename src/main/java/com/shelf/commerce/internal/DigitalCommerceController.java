package com.shelf.commerce.internal;

import com.shelf.accounts.Accounts;
import com.shelf.common.ApiFault;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/commerce")
class DigitalCommerceController {
    private final Accounts accounts;

    DigitalCommerceController(Accounts accounts) { this.accounts = accounts; }

    @GetMapping
    Overview overview(Principal principal) {
        accounts.current(principal);
        return new Overview(false, 0, List.of(), List.of());
    }

    @PostMapping({"/checkout", "/refunds", "/payouts"})
    void disabled(Principal principal) {
        accounts.verified(principal);
        throw ApiFault.unavailable("Digital payments and payouts are disabled.");
    }

    record Overview(boolean enabled, int platformCommissionMinor, List<LedgerEntry> ledger, List<Payout> payouts) {}
    record LedgerEntry(String reference, String status, long amountMinor, String currency) {}
    record Payout(String reference, String status, long amountMinor, String currency) {}
}
