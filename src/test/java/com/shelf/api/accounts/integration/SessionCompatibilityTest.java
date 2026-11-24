package com.shelf.api.accounts.integration;

import com.shelf.api.accounts.domain.SessionIdentity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCompatibilityTest {
    // Serialized with the two previously shipped principal classes, before the package rename.
    @ParameterizedTest
    @ValueSource(strings = {
            "rO0ABXNyADpjb20uc2hlbGYuYWNjb3VudHMuaW50ZXJuYWwuQWNjb3VudFNlcnZpY2UkU2Vzc2lvbklkZW50aXR5AAAAAAAAAAACAAJJAAd2ZXJzaW9uTAACaWR0ABBMamF2YS91dGlsL1VVSUQ7eHAAAAADc3IADmphdmEudXRpbC5VVUlEvJkD95hthS8CAAJKAAxsZWFzdFNpZ0JpdHNKAAttb3N0U2lnQml0c3hwAAAAAAAAAAEAAAAAAAAAAA==",
            "rO0ABXNyADFjb20uc2hlbGYuYWNjb3VudHMuaW50ZXJuYWwubW9kZWwuU2Vzc2lvbklkZW50aXR5AAAAAAAAAAACAAJJAAd2ZXJzaW9uTAACaWR0ABBMamF2YS91dGlsL1VVSUQ7eHAAAAADc3IADmphdmEudXRpbC5VVUlEvJkD95hthS8CAAJKAAxsZWFzdFNpZ0JpdHNKAAttb3N0U2lnQml0c3hwAAAAAAAAAAEAAAAAAAAAAA=="
    })
    void readsExistingJdbcSessions(String fixture) {
        var converters = new SessionSerializationConfig().springSessionConversionService();
        assertThat(converters.convert(Base64.getDecoder().decode(fixture), Object.class))
                .isEqualTo(new SessionIdentity(new UUID(0, 1), 3));
    }
}
