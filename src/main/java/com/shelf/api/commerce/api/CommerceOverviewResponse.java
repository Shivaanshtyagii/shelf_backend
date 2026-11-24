package com.shelf.api.commerce.api;

import java.util.List;

public record CommerceOverviewResponse(boolean enabled, int platformCommissionMinor, List<LedgerEntry> ledger,
        List<Payout> payouts) {
    public record LedgerEntry(String reference, String status, long amountMinor, String currency) {}
    public record Payout(String reference, String status, long amountMinor, String currency) {}
}
