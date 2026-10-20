package thorization.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.substitution.ParameterSubstitutionRule;
import thorization.core.substitution.ParameterSubstitutionEngine;
import thorization.util.HeaderUtils;

import java.util.Collections;
import java.util.List;

/**
 * Handles generating and replaying modified HTTP requests for configured roles.
 */
public class ReplayEngine {

    private final MontoyaApi api;
    private final DetectionEngine detectionEngine;
    private final ParameterSubstitutionEngine substitutionEngine;

    public ReplayEngine(MontoyaApi api, DetectionEngine detectionEngine) {
        this(api, detectionEngine, new ParameterSubstitutionEngine());
    }

    public ReplayEngine(MontoyaApi api, DetectionEngine detectionEngine, ParameterSubstitutionEngine substitutionEngine) {
        this.api = api;
        this.detectionEngine = detectionEngine;
        this.substitutionEngine = substitutionEngine != null ? substitutionEngine : new ParameterSubstitutionEngine();
    }

    /**
     * Executes replay tests for all active roles and optionally unauthenticated.
     */
    public void executeAudit(AuditRecord record, List<UserRole> roles, boolean testUnauthenticated) {
        executeAudit(record, roles, testUnauthenticated, Collections.emptyList());
    }

    public void executeAudit(AuditRecord record, List<UserRole> roles, boolean testUnauthenticated, List<ParameterSubstitutionRule> substitutionRules) {
        HttpRequest originalRequest = record.getOriginalRequest();
        HttpResponse originalResponse = record.getOriginalResponse();

        if (originalRequest == null) {
            return;
        }

        // 1. Test each configured and enabled role
        for (UserRole role : roles) {
            if (!role.isEnabled()) {
                continue;
            }

            try {
                HttpRequest modifiedRequest = HeaderUtils.applyRoleHeaders(originalRequest, role);
                if (substitutionEngine != null && substitutionRules != null && !substitutionRules.isEmpty()) {
                    modifiedRequest = substitutionEngine.applySubstitutions(modifiedRequest, role, substitutionRules);
                }
                long start = System.currentTimeMillis();
                HttpRequestResponse replayed = api != null && api.http() != null ? api.http().sendRequest(modifiedRequest) : null;
                long elapsed = System.currentTimeMillis() - start;

                HttpResponse response = replayed != null ? replayed.response() : null;
                DetectionEngine.EvaluationResult eval = detectionEngine.evaluate(originalResponse, response);

                int respLen = response != null && response.body() != null ? response.body().length() : 0;
                int statusCode = response != null ? response.statusCode() : 0;

                AuthorizationCheckResult result = new AuthorizationCheckResult(
                        role.getId(),
                        role.getName(),
                        eval.getVerdict(),
                        statusCode,
                        respLen,
                        eval.getSimilarityScore(),
                        elapsed,
                        eval.getNote(),
                        modifiedRequest,
                        response
                );

                record.addRoleResult(role.getId(), result);
            } catch (Exception e) {
                if (api != null && api.logging() != null) {
                    api.logging().logToError("Error replaying role '" + role.getName() + "': " + e.getMessage());
                }
            }
        }

        // 2. Test unauthenticated if enabled
        if (testUnauthenticated) {
            try {
                HttpRequest unauthRequest = HeaderUtils.stripAuthHeaders(originalRequest);
                if (substitutionEngine != null && substitutionRules != null && !substitutionRules.isEmpty()) {
                    unauthRequest = substitutionEngine.applySubstitutions(unauthRequest, null, substitutionRules);
                }
                long start = System.currentTimeMillis();
                HttpRequestResponse replayed = api != null && api.http() != null ? api.http().sendRequest(unauthRequest) : null;
                long elapsed = System.currentTimeMillis() - start;

                HttpResponse response = replayed != null ? replayed.response() : null;
                DetectionEngine.EvaluationResult eval = detectionEngine.evaluate(originalResponse, response);

                int respLen = response != null && response.body() != null ? response.body().length() : 0;
                int statusCode = response != null ? response.statusCode() : 0;

                AuthorizationCheckResult result = new AuthorizationCheckResult(
                        "unauthenticated",
                        "Unauthenticated",
                        eval.getVerdict(),
                        statusCode,
                        respLen,
                        eval.getSimilarityScore(),
                        elapsed,
                        eval.getNote(),
                        unauthRequest,
                        response
                );

                record.setUnauthenticatedResult(result);
            } catch (Exception e) {
                if (api != null && api.logging() != null) {
                    api.logging().logToError("Error replaying unauthenticated request: " + e.getMessage());
                }
            }
        }
    }
}

