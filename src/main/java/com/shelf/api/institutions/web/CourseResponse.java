package com.shelf.api.institutions.web;

import com.shelf.api.institutions.domain.Course;

import java.util.UUID;

public record CourseResponse(UUID id, String code, String name) {
    public static CourseResponse from(Course row) {
        return new CourseResponse(row.id(), row.code(), row.name());
    }
}
