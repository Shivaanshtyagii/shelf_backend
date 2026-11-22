package com.shelf.accounts;

import java.security.Principal;

public interface Accounts {
    Member current(Principal principal);
    Member verified(Principal principal);
    Member moderator(Principal principal);
}
