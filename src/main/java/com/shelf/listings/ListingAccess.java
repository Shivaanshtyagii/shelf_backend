package com.shelf.listings;

import com.shelf.accounts.Member;

import java.util.List;
import java.util.UUID;

public interface ListingAccess {
    Listing visible(UUID id, Member member);
    Listing owned(UUID id, UUID owner);
    List<Listing> queue();
    Listing decide(UUID id, int revision, Listing.Status result);
    Listing takeDown(UUID id);
}
