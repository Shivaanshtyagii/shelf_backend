package com.shelf.api.institutions.web;

import com.shelf.api.institutions.domain.Institution;

import java.util.UUID;

public record InstitutionResponse(UUID id, String name) {
    public static InstitutionResponse from(Institution row) {
        return new InstitutionResponse(row.id(), row.name());
    }
}
