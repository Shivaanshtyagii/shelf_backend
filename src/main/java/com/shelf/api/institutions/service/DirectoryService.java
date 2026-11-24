package com.shelf.api.institutions.service;

import com.shelf.api.accounts.api.Accounts;
import com.shelf.api.common.error.DomainException;
import com.shelf.api.institutions.api.Affiliations;
import com.shelf.api.institutions.domain.Course;
import com.shelf.api.institutions.domain.DirectoryRequest.Kind;
import com.shelf.api.institutions.domain.DirectoryRequest;
import com.shelf.api.institutions.domain.Institution;
import com.shelf.api.institutions.repository.DirectoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class DirectoryService implements Affiliations {
    private final DirectoryRepository directory;
    private final Accounts accounts;

    DirectoryService(Accounts accounts, DirectoryRepository directory) {
        this.accounts = accounts;
        this.directory = directory;
    }

    @Override
    public Optional<UUID> verifiedInstitution(UUID memberId) {
        return directory.verifiedInstitution(memberId);
    }

    public Optional<UUID> myInstitution(Principal principal) {
        return verifiedInstitution(accounts.current(principal).id());
    }

    @Override
    public UUID requireInstitution(UUID memberId) {
        return verifiedInstitution(memberId)
                .orElseThrow(() -> DomainException.forbidden("Verify your college affiliation to enter this campus."));
    }

    public List<Institution> search(String search) { return directory.search(search.strip()); }

    public List<Course> courses(UUID institution) { return directory.courses(institution); }

    @Transactional
    public void confirmDomain(Principal principal, UUID id) {
        var member = accounts.verified(principal);
        var domain = directory.officialDomain(id);
        var emailDomain = member.email().substring(member.email().lastIndexOf('@') + 1);
        if (domain.isEmpty() || !domain.get().equalsIgnoreCase(emailDomain)) {
            throw DomainException.forbidden("The verified account email does not match this college. Submit a verification request.");
        }
        directory.affiliate(member.id(), id);
    }

    public UUID request(Principal principal, Kind kind, UUID institution, String name, String detail) {
        var member = accounts.verified(principal).id();
        if ((kind != Kind.INSTITUTION && institution == null)
                || (kind != Kind.AFFILIATION && (name == null || name.isBlank()))) {
            throw DomainException.badRequest("Provide the college and request details.");
        }
        var id = UUID.randomUUID();
        directory.insertRequest(id, member, kind, kind == Kind.INSTITUTION ? null : institution,
                name == null ? null : name.strip(), detail == null ? null : detail.strip());
        return id;
    }

    public List<DirectoryRequest> requestsFor(Principal principal) {
        var member = accounts.current(principal).id();
        return directory.requestsFor(member);
    }

    public List<DirectoryRequest> pendingRequests(Principal principal) {
        accounts.moderator(principal);
        return directory.pendingRequests();
    }

    @Transactional
    public void review(UUID id, Principal principal, boolean approved, String verifiedValue) {
        var moderator = accounts.moderator(principal).id();
        var request = directory.reviewRequest(id, moderator, approved ? "APPROVED" : "REJECTED")
                .orElseThrow(() -> DomainException.conflict("This request is no longer pending."));
        if (!approved) return;
        if (verifiedValue == null) verifiedValue = "";

        switch (request.kind()) {
            case INSTITUTION -> {
                var domain = verifiedValue.strip().toLowerCase(Locale.ROOT);
                if (!domain.isEmpty() && !domain.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}")) {
                    throw DomainException.badRequest("Enter an official email domain such as college.edu.");
                }
                directory.insertInstitution(UUID.randomUUID(), request.name(), domain.isEmpty() ? null : domain);
            }
            case COURSE -> {
                var code = verifiedValue.strip();
                if (code.isEmpty() || code.length() > 30) throw DomainException.badRequest("Enter the official course code.");
                directory.insertCourse(UUID.randomUUID(), request.institutionId(), code, request.name());
            }
            case AFFILIATION -> directory.affiliate(request.memberId(), request.institutionId());
        }
    }
}
