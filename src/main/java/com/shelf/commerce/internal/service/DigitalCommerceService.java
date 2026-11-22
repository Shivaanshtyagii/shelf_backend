package com.shelf.commerce.internal.service;

import com.shelf.commerce.CommerceAvailability;
import com.shelf.commerce.internal.model.CommerceOverview;
import com.shelf.common.ApiFault;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DigitalCommerceService implements CommerceAvailability {
    @Override
    public boolean enabled() {
        return false;
    }

    public CommerceOverview overview() {
        return new CommerceOverview(enabled(), 0, List.of(), List.of());
    }

    public void requireAvailable() {
        throw ApiFault.unavailable("Digital payments and payouts are disabled.");
    }
}
