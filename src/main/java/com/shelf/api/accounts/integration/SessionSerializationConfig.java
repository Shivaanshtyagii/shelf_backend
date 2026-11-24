package com.shelf.api.accounts.integration;

import com.shelf.api.accounts.domain.SessionIdentity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

import java.io.IOException;
import java.io.ObjectStreamClass;

@Configuration
class SessionSerializationConfig {
    @Bean
    GenericConversionService springSessionConversionService() {
        var converters = new GenericConversionService();
        converters.addConverter(Object.class, byte[].class, new SerializingConverter());
        converters.addConverter(byte[].class, Object.class, new DeserializingConverter(input -> {
            try (var stream = new ConfigurableObjectInputStream(input, getClass().getClassLoader()) {
                @Override
                protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                    var descriptor = super.readClassDescriptor();
                    // Both historical records had exactly the same serialized fields (id, version).
                    return switch (descriptor.getName()) {
                        case "com.shelf.accounts.internal.AccountService$SessionIdentity",
                             "com.shelf.accounts.internal.model.SessionIdentity" -> ObjectStreamClass.lookup(SessionIdentity.class);
                        default -> descriptor;
                    };
                }
            }) {
                return stream.readObject();
            } catch (ClassNotFoundException ex) {
                throw new IOException("Unknown session attribute type", ex);
            }
        }));
        return converters;
    }
}
