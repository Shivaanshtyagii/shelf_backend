package com.shelf.api.listings.service;

import com.shelf.api.accounts.api.Accounts;
import com.shelf.api.common.error.DomainException;
import com.shelf.api.listings.api.SellerReview;
import com.shelf.api.listings.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Service
public class SellerService implements SellerReview {
    private final SellerRepository sellers;
    private final Accounts accounts;

    SellerService(Accounts accounts, SellerRepository sellers) {
        this.accounts = accounts;
        this.sellers = sellers;
    }

    public String sellerStatus(Principal principal) {
        var owner = accounts.current(principal).id();
        return sellers.sellerStatus(owner);
    }
    @Transactional
    public String applyToSell(Principal principal) {
        var owner = accounts.verified(principal).id();
        sellers.applyToSell(owner);
        return sellers.sellerStatus(owner);
    }

    void requireApproved(UUID owner) {
        if (!"APPROVED".equals(sellers.sellerStatus(owner))) {
            throw DomainException.forbidden("Seller onboarding must be approved before submitting.");
        }
    }

    @Override
    public List<UUID> sellerQueue() { return sellers.sellerQueue(); }

    @Override
    public void decideSeller(UUID memberId, boolean approved) {
        if (!sellers.decideSeller(memberId, approved)) throw DomainException.conflict("This seller request is no longer pending.");
    }
}
