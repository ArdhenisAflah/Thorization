package thorization.core.pipeline;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import thorization.core.DetectionEngine;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineExecutionResult;
import thorization.model.pipeline.PipelineStep;
import thorization.model.pipeline.RequestStep;
import thorization.model.pipeline.StepExecutionRecord;
import thorization.model.pipeline.StepType;
import thorization.util.HeaderUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asynchronous execution engine that runs multi-step stateful Pipeline chains,
 * performs role credential injection, resolves dynamic {{VAR}} placeholders,
 * extracts response/request variables, and evaluates conditional branches.
 */
public class PipelineExecutor {

    private final MontoyaApi api;
    private final DetectionEngine detectionEngine;

    public PipelineExecutor(MontoyaApi api, DetectionEngine detectionEngine) {
        this.api = api;
        this.detectionEngine = detectionEngine != null ? detectionEngine : new DetectionEngine();
    }

    public PipelineExecutionResult execute(Pipeline pipeline, List<UserRole> availableRoles, boolean testUnauthenticated) {
        if (pipeline == null) {
            return new PipelineExecutionResult("unknown", "Empty Pipeline");
        }

        long startTime = System.currentTimeMillis();
        PipelineExecutionResult result = new PipelineExecutionResult(pipeline.getId(), pipeline.getName());
        Map<String, String> variables = new LinkedHashMap<>();
        if (pipeline.getInitialVariables() != null) {
            variables.putAll(pipeline.getInitialVariables());
        }

        StringBuilder trace = new StringBuilder();
        trace.append("=================================================================\n");
        trace.append(String.format("  THORIZATION PIPELINE EXECUTION: \"%s\"\n", pipeline.getName()));
        trace.append("=================================================================\n");
        trace.append("Triggered: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        HttpResponse lastResponse = null;
        HttpRequest lastRequest = null;
        Verdict computedVerdict = Verdict.INDETERMINABLE;

        List<PipelineStep> steps = pipeline.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            PipelineStep step = steps.get(i);
            if (!step.isEnabled()) {
                trace.append(String.format("[Step %d: %s] [SKIPPED - Disabled]\n", i + 1, step.getName()));
                continue;
            }

            if (step instanceof RequestStep) {
                RequestStep reqStep = (RequestStep) step;
                StepExecutionRecord stepRec = executeRequestStep(i + 1, reqStep, variables, availableRoles, trace);
                result.addStepRecord(stepRec);

                if (stepRec.getResponse() != null) {
                    lastResponse = stepRec.getResponse();
                    lastRequest = stepRec.getRequest();
                }
            } else if (step instanceof ConditionBranch) {
                ConditionBranch branch = (ConditionBranch) step;
                StepExecutionRecord branchRec = new StepExecutionRecord(branch.getId(), branch.getName(), StepType.CONDITION_BRANCH);

                boolean conditionPassed = evaluateCondition(branch, lastResponse, lastRequest, variables, trace);
                branchRec.setSuccess(conditionPassed);

                ConditionBranch.BranchAction action = conditionPassed ? branch.getOnTrueAction() : branch.getOnFalseAction();
                trace.append(String.format("   └─ Action: %s\n", action.getLabel()));

                if (action == ConditionBranch.BranchAction.MARK_VERDICT_BYPASS) {
                    computedVerdict = Verdict.BYPASS;
                    result.addStepRecord(branchRec);
                    break;
                } else if (action == ConditionBranch.BranchAction.MARK_VERDICT_ENFORCED) {
                    computedVerdict = Verdict.ENFORCED;
                    result.addStepRecord(branchRec);
                    break;
                } else if (action == ConditionBranch.BranchAction.STOP_PIPELINE) {
                    trace.append(String.format("   └─ Pipeline aborted: %s\n", branch.getFailureNote()));
                    result.addStepRecord(branchRec);
                    break;
                }
                result.addStepRecord(branchRec);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        result.setTotalDurationMs(duration);
        result.setResolvedVariables(variables);
        result.setFinalVerdict(computedVerdict);
        result.setCompleted(true);

        trace.append("\n--- DYNAMIC VARIABLES RESOLVED ---\n");
        if (variables.isEmpty()) {
            trace.append("  (None)\n");
        } else {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                trace.append(String.format("  • {{%s}} ➔ \"%s\"\n", entry.getKey(), entry.getValue()));
            }
        }
        trace.append("\nFinal Verdict: ").append(computedVerdict.getDisplayName()).append(" | Duration: ").append(duration).append(" ms\n");

        result.setExecutionTrace(trace.toString());
        return result;
    }

    private StepExecutionRecord executeRequestStep(int stepNum, RequestStep reqStep, Map<String, String> variables,
                                                   List<UserRole> availableRoles, StringBuilder trace) {
        StepExecutionRecord record = new StepExecutionRecord(reqStep.getId(), reqStep.getName(), StepType.REQUEST);
        long start = System.currentTimeMillis();

        String resolvedUrl = VariableResolver.resolve(reqStep.getUrlTemplate(), variables);
        String resolvedBody = VariableResolver.resolve(reqStep.getBodyTemplate(), variables);
        String resolvedHeaders = VariableResolver.resolve(reqStep.getHeadersTemplate(), variables);

        UserRole targetRole = findRole(reqStep.getTargetRoleId(), availableRoles);
        String roleLabel = targetRole != null ? targetRole.getName() : "Unauthenticated / Default";
        record.setRoleName(roleLabel);

        trace.append(String.format("[Step %d: %s] %s %s (Role: %s)\n", stepNum, reqStep.getName(), reqStep.getMethod(), resolvedUrl, roleLabel));

        try {
            HttpRequest httpRequest = null;
            HttpRequestResponse response = null;

            if (api != null && api.http() != null) {
                httpRequest = HttpRequest.httpRequestFromUrl(resolvedUrl).withMethod(reqStep.getMethod());
                if (resolvedBody != null && !resolvedBody.isEmpty()) {
                    httpRequest = httpRequest.withBody(resolvedBody);
                }

                // Inject custom template headers
                if (resolvedHeaders != null && !resolvedHeaders.trim().isEmpty()) {
                    for (String line : resolvedHeaders.split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && trimmed.contains(":")) {
                            String[] parts = trimmed.split(":", 2);
                            httpRequest = HeaderUtils.setHeader(httpRequest, parts[0].trim(), parts[1].trim());
                        }
                    }
                }

                // Inject Role Credentials
                if (targetRole != null) {
                    httpRequest = HeaderUtils.applyRoleHeaders(httpRequest, targetRole);
                } else if ("UNAUTHENTICATED".equalsIgnoreCase(reqStep.getTargetRoleId())) {
                    httpRequest = HeaderUtils.stripAuthHeaders(httpRequest);
                }

                response = api.http().sendRequest(httpRequest);
            }

            record.setHttpRequestResponse(response);
            record.setExecutionTimeMs(System.currentTimeMillis() - start);

            if (response != null && response.response() != null) {
                int status = response.response().statusCode();
                int len = response.response().body() != null ? response.response().body().length() : 0;
                trace.append(String.format("   └─ Response: HTTP %d | Length: %d bytes\n", status, len));
            } else {
                trace.append("   └─ Response: [Simulated / Local Execution]\n");
            }

            // Perform Extractions
            if (reqStep.getExtractions() != null) {
                for (ExtractionRule rule : reqStep.getExtractions()) {
                    String extractedVal = performExtraction(rule, httpRequest, response != null ? response.response() : null, resolvedBody, variables);
                    if (extractedVal != null && !extractedVal.isEmpty()) {
                        variables.put(rule.getVariableName(), extractedVal);
                        record.addExtractedVar(rule.getVariableName(), extractedVal);
                        trace.append(String.format("   └─ Extracted: {{%s}} = \"%s\" (%s via %s)\n",
                                rule.getVariableName(), extractedVal, rule.getSource().getDisplayName(), rule.getType().getDisplayName()));
                    } else if (rule.getDefaultValue() != null && !rule.getDefaultValue().isEmpty()) {
                        variables.put(rule.getVariableName(), rule.getDefaultValue());
                        record.addExtractedVar(rule.getVariableName(), rule.getDefaultValue());
                    }
                }
            }

        } catch (Exception e) {
            record.setSuccess(false);
            trace.append(String.format("   └─ Error executing step: %s\n", e.getMessage()));
            if (api != null && api.logging() != null) {
                api.logging().logToError("Pipeline Step Error: " + e.getMessage());
            }
        }

        return record;
    }

    private String performExtraction(ExtractionRule rule, HttpRequest request, HttpResponse response,
                                     String requestBody, Map<String, String> currentVars) {
        if (rule == null) return null;

        ExtractionSource source = rule.getSource();
        ExtractionType type = rule.getType();
        String expr = rule.getExpression();

        String rawContent = "";
        if (source == ExtractionSource.REQUEST_BODY) {
            rawContent = request != null && request.body() != null ? request.bodyToString() : requestBody;
        } else if (source == ExtractionSource.RESPONSE_BODY) {
            rawContent = response != null && response.body() != null ? response.bodyToString() : "";
        } else if (source == ExtractionSource.REQUEST_HEADER) {
            if (request != null && expr != null) {
                HttpHeader h = request.header(expr);
                return h != null ? h.value() : null;
            }
        } else if (source == ExtractionSource.RESPONSE_HEADER) {
            if (response != null && expr != null) {
                HttpHeader h = response.header(expr);
                return h != null ? h.value() : null;
            }
        } else if (source == ExtractionSource.URL_QUERY_PARAM) {
            String url = request != null ? request.url() : "";
            return JsonPathExtractor.extractRegex(url, "[?&]" + PatternQuote(expr) + "=([^&#]+)");
        }

        if (rawContent == null || rawContent.isEmpty()) {
            return null;
        }

        if (type == ExtractionType.JSON_PATH) {
            return JsonPathExtractor.extractJsonPath(rawContent, expr);
        } else if (type == ExtractionType.REGEX) {
            return JsonPathExtractor.extractRegex(rawContent, expr);
        }

        return null;
    }

    private boolean evaluateCondition(ConditionBranch branch, HttpResponse response, HttpRequest request,
                                      Map<String, String> variables, StringBuilder trace) {
        ConditionBranch.ConditionType type = branch.getConditionType();
        String expected = VariableResolver.resolve(branch.getExpectedValue(), variables);

        trace.append(String.format("   • Evaluating: IF %s == \"%s\"\n", type.getLabel(), expected));

        if (type == ConditionBranch.ConditionType.STATUS_CODE_EQUALS) {
            if (response == null) return false;
            try {
                int expectedCode = Integer.parseInt(expected.trim());
                return response.statusCode() == expectedCode;
            } catch (Exception e) {
                return false;
            }
        } else if (type == ConditionBranch.ConditionType.BODY_CONTAINS) {
            if (response == null || response.body() == null) return false;
            return response.bodyToString().contains(expected);
        } else if (type == ConditionBranch.ConditionType.BODY_MATCHES_REGEX) {
            if (response == null || response.body() == null) return false;
            return response.bodyToString().matches("(?s).*" + expected + ".*");
        } else if (type == ConditionBranch.ConditionType.VARIABLE_NOT_EMPTY) {
            String val = variables.get(expected);
            return val != null && !val.trim().isEmpty();
        }

        return false;
    }

    private UserRole findRole(String roleId, List<UserRole> roles) {
        if (roleId == null || roles == null) return null;
        for (UserRole r : roles) {
            if (r.getId().equals(roleId) || r.getName().equalsIgnoreCase(roleId)) {
                return r;
            }
        }
        return null;
    }

    private String PatternQuote(String s) {
        if (s == null) return "";
        return java.util.regex.Pattern.quote(s);
    }
}
