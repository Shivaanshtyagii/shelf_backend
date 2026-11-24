package com.shelf.api.accounts.api;

import java.security.Principal;

public interface Accounts {
    Member current(Principal principal);
    Member verified(Principal principal);
    Member moderator(Principal principal);
}
