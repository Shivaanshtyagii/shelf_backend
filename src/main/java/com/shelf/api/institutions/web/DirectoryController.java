package com.shelf.api.institutions.web;

import com.shelf.api.institutions.domain.DirectoryRequest.Kind;
import com.shelf.api.institutions.service.DirectoryService;
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
    private final DirectoryService directory;

    DirectoryController(DirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    List<InstitutionResponse> search(@RequestParam(defaultValue = "") @Size(max = 120) String q) {
        return directory.search(q).stream().map(InstitutionResponse::from).toList();
    }

    @GetMapping("/{id}/courses")
    List<CourseResponse> courses(@PathVariable UUID id) {
        return directory.courses(id).stream().map(CourseResponse::from).toList();
    }

    @GetMapping("/mine")
    Campus mine(Principal principal) {
        return new Campus(directory.myInstitution(principal).orElse(null));
    }

    @PostMapping("/{id}/verify")
    void verify(Principal principal, @PathVariable UUID id) { directory.confirmDomain(principal, id); }

    @PostMapping("/requests")
    RequestId request(Principal principal, @Valid @RequestBody DirectorySubmissionRequest input) {
        return new RequestId(directory.request(principal,
                input.kind(), input.institutionId(), input.name(), input.detail()));
    }

    @GetMapping("/requests")
    List<DirectoryRequestResponse> mineRequests(Principal principal) {
        return directory.requestsFor(principal).stream().map(DirectoryRequestResponse::from).toList();
    }

    @GetMapping("/review")
    List<DirectoryRequestResponse> queue(Principal principal) {
        return directory.pendingRequests(principal).stream().map(DirectoryRequestResponse::from).toList();
    }

    @PostMapping("/review/{id}")
    void decide(Principal principal, @PathVariable UUID id, @Valid @RequestBody DecisionRequest input) {
        directory.review(id, principal, input.approved(),
                input.verifiedValue());
    }

    record Campus(UUID institutionId) {}
    record RequestId(UUID id) {}
    record DirectorySubmissionRequest(@NotNull Kind kind, UUID institutionId,
                   @Size(max = 140) String name, @Size(max = 600) String detail) {}
    record DecisionRequest(boolean approved, @Size(max = 253) String verifiedValue) {}
}
