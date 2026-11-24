package com.shelf.api.listings.domain;

public record ListingSearch(String q, String subject, Sort sort, int page, int size) {
    public enum Sort { RELEVANCE, NEWEST, PRICE_LOW, PRICE_HIGH }
}
