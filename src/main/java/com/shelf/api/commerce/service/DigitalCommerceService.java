package com.shelf.api.commerce.service;

import com.shelf.api.accounts.api.Accounts;
import com.shelf.api.commerce.api.CommerceAvailability;
import com.shelf.api.commerce.api.CommerceOverviewResponse;
import com.shelf.api.common.error.DomainException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

@Service
public class DigitalCommerceService implements CommerceAvailability {
    private final Accounts accounts;

    DigitalCommerceService(Accounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    public CommerceOverviewResponse overview(Principal principal) {
        accounts.current(principal);
        return new CommerceOverviewResponse(enabled(), 0, List.of(), List.of());
    }

    public void requireAvailable(Principal principal) {
        accounts.verified(principal);
        throw DomainException.unavailable("Digital payments and payouts are disabled.");
    }
}
