package com.shelf.api.common.error;

import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

final class ApiError {
    private ApiError() {}

    static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        problem.setProperty("traceId", MDC.get("requestId"));
        return problem;
    }
}
