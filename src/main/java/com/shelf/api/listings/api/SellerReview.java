package com.shelf.api.listings.api;

import java.util.List;
import java.util.UUID;

public interface SellerReview {
    List<UUID> sellerQueue();
    void decideSeller(UUID memberId, boolean approved);
}
