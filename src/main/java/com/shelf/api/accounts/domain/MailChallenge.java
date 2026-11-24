package com.shelf.api.accounts.domain;

public record MailChallenge(String email, ChallengePurpose purpose, String token) {}
