package com.shelf.api.common.util;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestIds {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestIds() {}

    public static String acceptOrGenerate(String supplied) {
        return supplied != null && VALID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
    }
}
