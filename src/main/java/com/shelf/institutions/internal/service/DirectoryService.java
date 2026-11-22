package com.shelf.institutions.internal.service;

import com.shelf.accounts.Member;
import com.shelf.common.ApiFault;
import com.shelf.institutions.Affiliations;
import com.shelf.institutions.internal.model.Course;
import com.shelf.institutions.internal.model.DirectoryRequest;
import com.shelf.institutions.internal.model.DirectoryRequest.Kind;
import com.shelf.institutions.internal.model.Institution;
import com.shelf.institutions.internal.persistence.DirectoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class DirectoryService implements Affiliations {
    private final DirectoryRepository directory;

    DirectoryService(DirectoryRepository directory) { this.directory = directory; }

    @Override
    public Optional<UUID> verifiedInstitution(UUID memberId) {
        return directory.verifiedInstitution(memberId);
    }

    @Override
    public UUID requireInstitution(UUID memberId) {
        return verifiedInstitution(memberId)
                .orElseThrow(() -> ApiFault.forbidden("Verify your college affiliation to enter this campus."));
    }

    public List<Institution> search(String search) { return directory.search(search.strip()); }

    public List<Course> courses(UUID institution) { return directory.courses(institution); }

    @Transactional
    public void confirmDomain(Member member, UUID id) {
        var domain = directory.officialDomain(id);
        var emailDomain = member.email().substring(member.email().lastIndexOf('@') + 1);
        if (domain.isEmpty() || !domain.get().equalsIgnoreCase(emailDomain)) {
            throw ApiFault.forbidden("The verified account email does not match this college. Submit a verification request.");
        }
        directory.affiliate(member.id(), id);
    }

    public UUID request(UUID member, Kind kind, UUID institution, String name, String detail) {
        if ((kind != Kind.INSTITUTION && institution == null)
                || (kind != Kind.AFFILIATION && (name == null || name.isBlank()))) {
            throw ApiFault.badRequest("Provide the college and request details.");
        }
        var id = UUID.randomUUID();
        directory.insertRequest(id, member, kind, kind == Kind.INSTITUTION ? null : institution,
                name == null ? null : name.strip(), detail == null ? null : detail.strip());
        return id;
    }

    public List<DirectoryRequest> requestsFor(UUID member) { return directory.requestsFor(member); }

    public List<DirectoryRequest> pendingRequests() { return directory.pendingRequests(); }

    @Transactional
    public void review(UUID id, UUID moderator, boolean approved, String verifiedValue) {
        var request = directory.reviewRequest(id, moderator, approved ? "APPROVED" : "REJECTED")
                .orElseThrow(() -> ApiFault.conflict("This request is no longer pending."));
        if (!approved) return;

        switch (request.kind()) {
            case INSTITUTION -> {
                var domain = verifiedValue.strip().toLowerCase(Locale.ROOT);
                if (!domain.isEmpty() && !domain.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}")) {
                    throw ApiFault.badRequest("Enter an official email domain such as college.edu.");
                }
                directory.insertInstitution(UUID.randomUUID(), request.name(), domain.isEmpty() ? null : domain);
            }
            case COURSE -> {
                var code = verifiedValue.strip();
                if (code.isEmpty() || code.length() > 30) throw ApiFault.badRequest("Enter the official course code.");
                directory.insertCourse(UUID.randomUUID(), request.institutionId(), code, request.name());
            }
            case AFFILIATION -> directory.affiliate(request.memberId(), request.institutionId());
        }
    }
}
