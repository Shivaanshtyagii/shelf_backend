package com.shelf.listings.internal.service;

import com.shelf.common.ApiFault;
import com.shelf.listings.SellerReview;
import com.shelf.listings.internal.persistence.SellerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SellerService implements SellerReview {
    private final SellerRepository sellers;

    SellerService(SellerRepository sellers) {
        this.sellers = sellers;
    }

    public String sellerStatus(UUID owner) { return sellers.sellerStatus(owner); }
    public void applyToSell(UUID owner) { sellers.applyToSell(owner); }

    void requireApproved(UUID owner) {
        if (!"APPROVED".equals(sellers.sellerStatus(owner))) {
            throw ApiFault.forbidden("Seller onboarding must be approved before submitting.");
        }
    }

    @Override
    public List<UUID> sellerQueue() { return sellers.sellerQueue(); }

    @Override
    public void decideSeller(UUID memberId, boolean approved) {
        if (!sellers.decideSeller(memberId, approved)) throw ApiFault.conflict("This seller request is no longer pending.");
    }
}
