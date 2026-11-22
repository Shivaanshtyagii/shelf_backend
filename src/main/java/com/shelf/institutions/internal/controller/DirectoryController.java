package com.shelf.institutions.internal.controller;

import com.shelf.accounts.Accounts;
import com.shelf.institutions.internal.model.Course;
import com.shelf.institutions.internal.model.DirectoryRequest;
import com.shelf.institutions.internal.model.DirectoryRequest.Kind;
import com.shelf.institutions.internal.model.Institution;
import com.shelf.institutions.internal.service.DirectoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/institutions")
class DirectoryController {
    private final Accounts accounts;
    private final DirectoryService directory;

    DirectoryController(Accounts accounts, DirectoryService directory) {
        this.accounts = accounts;
        this.directory = directory;
    }

    @GetMapping
    List<Institution> search(@RequestParam(defaultValue = "") @Size(max = 120) String q) {
        return directory.search(q);
    }

    @GetMapping("/{id}/courses")
    List<Course> courses(@PathVariable UUID id) { return directory.courses(id); }

    @GetMapping("/mine")
    Campus mine(Principal principal) {
        return new Campus(directory.verifiedInstitution(accounts.current(principal).id()).orElse(null));
    }

    @PostMapping("/{id}/verify")
    void verify(Principal principal, @PathVariable UUID id) { directory.confirmDomain(accounts.verified(principal), id); }

    @PostMapping("/requests")
    RequestId request(Principal principal, @Valid @RequestBody Request input) {
        return new RequestId(directory.request(accounts.verified(principal).id(),
                input.kind(), input.institutionId(), input.name(), input.detail()));
    }

    @GetMapping("/requests")
    List<DirectoryRequest> mineRequests(Principal principal) {
        return directory.requestsFor(accounts.current(principal).id());
    }

    @GetMapping("/review")
    List<DirectoryRequest> queue(Principal principal) {
        accounts.moderator(principal);
        return directory.pendingRequests();
    }

    @PostMapping("/review/{id}")
    void decide(Principal principal, @PathVariable UUID id, @Valid @RequestBody Decision input) {
        directory.review(id, accounts.moderator(principal).id(), input.approved(),
                input.verifiedValue() == null ? "" : input.verifiedValue());
    }

    record Campus(UUID institutionId) {}
    record RequestId(UUID id) {}
    record Request(@NotNull Kind kind, UUID institutionId,
                   @Size(max = 140) String name, @Size(max = 600) String detail) {}
    record Decision(boolean approved, @Size(max = 253) String verifiedValue) {}
}
