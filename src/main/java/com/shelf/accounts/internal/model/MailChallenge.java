package com.shelf.accounts.internal.model;

public record MailChallenge(String email, ChallengePurpose purpose, String token) {}
