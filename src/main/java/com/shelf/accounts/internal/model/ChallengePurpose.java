package com.shelf.accounts.internal.model;

public enum ChallengePurpose {
    VERIFY(1440),
    RESET(30);

    private final int validityMinutes;

    ChallengePurpose(int validityMinutes) {
        this.validityMinutes = validityMinutes;
    }

    public int validityMinutes() {
        return validityMinutes;
    }
}
