package com.shelf.accounts.internal;

import com.shelf.accounts.internal.model.SessionIdentity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCompatibilityTest {
    @Test
    void readsAnIdentityFromASessionStoredBeforeTheLayerSplit() throws Exception {
        var id = UUID.randomUUID();
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(new AccountService.SessionIdentity(id, 3));
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(new SessionIdentity(id, 3));
        }
    }
}
