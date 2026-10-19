package thorization;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import burp.api.montoya.logging.Logging;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thorization.core.DetectionEngine;
import thorization.core.pipeline.PipelineExecutor;
import thorization.core.pipeline.VariableResolver;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class PipelineStudioComprehensiveTest {

    private static MontoyaObjectFactory originalFactory;
    private MontoyaApi mockApi;
    private Http mockHttp;
    private final Map<String, Function<HttpRequest, HttpResponse>> mockHttpResponses = new ConcurrentHashMap<>();
    private final List<HttpRequest> recordedRequests = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void initFactory() {
        originalFactory = ObjectFactoryLocator.FACTORY;
        ObjectFactoryLocator.FACTORY = createMockObjectFactory();
    }

    @AfterAll
    static void restoreFactory() {
        ObjectFactoryLocator.FACTORY = originalFactory;
    }

    @BeforeEach
    void setUp() {
        mockHttpResponses.clear();
        recordedRequests.clear();

        mockHttp = (Http) Proxy.newProxyInstance(
                Http.class.getClassLoader(),
                new Class<?>[]{Http.class},
                (proxy, method, args) -> {
                    if ("sendRequest".equals(method.getName()) && args != null && args.length >= 1) {
                        HttpRequest req = (HttpRequest) args[0];
                        recordedRequests.add(req);

                        Function<HttpRequest, HttpResponse> handler = mockHttpResponses.get(req.url());
                        if (handler == null) {
                            // Prefix match fallback
                            for (Map.Entry<String, Function<HttpRequest, HttpResponse>> entry : mockHttpResponses.entrySet()) {
                                if (req.url().startsWith(entry.getKey())) {
                                    handler = entry.getValue();
                                    break;
                                }
                            }
                        }

                        HttpResponse resp = handler != null ? handler.apply(req) : createMockResponse(200, "{\"status\": \"ok\"}", Collections.emptyMap());
                        return createMockHttpRequestResponse(req, resp);
                    }
                    return null;
                }
        );

        Logging mockLogging = (Logging) Proxy.newProxyInstance(
                Logging.class.getClassLoader(),
                new Class<?>[]{Logging.class},
                (proxy, method, args) -> null
        );

        mockApi = (MontoyaApi) Proxy.newProxyInstance(
                MontoyaApi.class.getClassLoader(),
                new Class<?>[]{MontoyaApi.class},
                (proxy, method, args) -> {
                    if ("http".equals(method.getName())) return mockHttp;
                    if ("logging".equals(method.getName())) return mockLogging;
                    return null;
                }
        );
    }

    // =========================================================================
    // 1. MONTOYA API CALLS & ROLE CREDENTIAL INJECTION
    // =========================================================================

    @Test
    void testMontoyaHttpCallWithRoleCredentialsAndCustomHeaders() {
        UserRole doctorRole = new UserRole("Doctor A", "X-Doctor-Token: doc123\nAuthorization: Bearer doc-auth", "doc_session=sessA");
        doctorRole.setId("DOC-001");
        List<UserRole> roles = List.of(doctorRole);

        RequestStep step = new RequestStep("Step 1: Create Prescription", "POST", "https://hospital.local/api/prescriptions", "DOC-001");
        step.setHeadersTemplate("X-Clinic-ID: Clinic-44\nContent-Type: application/json");
        step.setBodyTemplate("{\"patient\": \"John Doe\", \"med\": \"Aspirin\"}");

        mockHttpResponses.put("https://hospital.local/api/prescriptions", req -> {
            // Verify Montoya HttpRequest passed to sendRequest
            assertEquals("POST", req.method());
            assertTrue(req.hasHeader("X-Doctor-Token"));
            assertEquals("doc123", req.header("X-Doctor-Token").value());
            assertEquals("Bearer doc-auth", req.header("Authorization").value());
            assertEquals("doc_session=sessA", req.header("Cookie").value());
            assertEquals("Clinic-44", req.header("X-Clinic-ID").value());
            assertEquals("{\"patient\": \"John Doe\", \"med\": \"Aspirin\"}", req.bodyToString());
            return createMockResponse(201, "{\"id\": 991, \"status\": \"created\"}", Map.of("Location", "/api/prescriptions/991"));
        });

        Pipeline pipeline = new Pipeline("Prescription Test", "Tests creation");
        pipeline.addStep(step);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, roles, false);

        assertTrue(result.isCompleted());
        assertEquals(1, result.getStepRecords().size());
        System.out.println("TRACE:\n" + result.getExecutionTrace());
        StepExecutionRecord rec = result.getStepRecords().get(0);
        assertEquals(201, rec.getStatusCode());
        assertNotNull(rec.getHttpRequestResponse());
        assertEquals(1, recordedRequests.size());
    }

    @Test
    void testMontoyaHttpCallWithUnauthenticatedRoleStripsAuthHeaders() {
        RequestStep step = new RequestStep("Step: Anonymous Access", "GET", "https://hospital.local/api/public", "UNAUTHENTICATED");
        step.setHeadersTemplate("Authorization: Bearer should-be-removed\nCookie: session=remove-me\nX-Custom: keep-me");

        mockHttpResponses.put("https://hospital.local/api/public", req -> {
            assertFalse(req.hasHeader("Authorization"), "Authorization header should have been stripped");
            assertFalse(req.hasHeader("Cookie"), "Cookie header should have been stripped");
            assertTrue(req.hasHeader("X-Custom"));
            assertEquals("keep-me", req.header("X-Custom").value());
            return createMockResponse(200, "{\"public\": true}", Collections.emptyMap());
        });

        Pipeline pipeline = new Pipeline("Public Access Test", "Test unauth");
        pipeline.addStep(step);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), true);

        assertTrue(result.isCompleted());
        StepExecutionRecord rec = result.getStepRecords().get(0);
        assertEquals(200, rec.getStatusCode());
    }

    // =========================================================================
    // 2. MAPPING PLACEHOLDER RULES (VariableResolver)
    // =========================================================================

    @Test
    void testDynamicPlaceholderMappingAcrossAllFields() {
        Map<String, String> initialVars = new HashMap<>();
        initialVars.put("API_VERSION", "v2");
        initialVars.put("TENANT_ID", "tenant-alpha");
        initialVars.put("RESOURCE_ID", "RES-5501");
        initialVars.put("USER_NAME", "Alice");

        Pipeline pipeline = new Pipeline("Placeholder Mapping Test", "Tests resolution");
        pipeline.setInitialVariables(initialVars);

        RequestStep step = new RequestStep("Step 1", "PUT",
                "https://api.local/{{API_VERSION}}/tenants/{{TENANT_ID}}/resources/{{RESOURCE_ID}}?user={{USER_NAME}}&unresolved={{MISSING_VAR}}",
                "UNAUTHENTICATED");
        step.setHeadersTemplate("X-Tenant: {{TENANT_ID}}\nX-Unresolved: {{NOT_FOUND}}");
        step.setBodyTemplate("{\"tenant\": \"{{TENANT_ID}}\", \"owner\": \"{{USER_NAME}}\", \"id\": \"{{RESOURCE_ID}}\"}");

        mockHttpResponses.put("https://api.local/v2/tenants/tenant-alpha/resources/RES-5501?user=Alice&unresolved={{MISSING_VAR}}", req -> {
            assertEquals("tenant-alpha", req.header("X-Tenant").value());
            assertEquals("{{NOT_FOUND}}", req.header("X-Unresolved").value());
            assertTrue(req.bodyToString().contains("\"tenant\": \"tenant-alpha\""));
            assertTrue(req.bodyToString().contains("\"owner\": \"Alice\""));
            assertTrue(req.bodyToString().contains("\"id\": \"RES-5501\""));
            return createMockResponse(200, "{\"success\": true}", Collections.emptyMap());
        });

        pipeline.addStep(step);
        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), false);

        assertTrue(result.isCompleted());
        assertEquals("tenant-alpha", result.getResolvedVariables().get("TENANT_ID"));
        assertEquals("RES-5501", result.getResolvedVariables().get("RESOURCE_ID"));
    }

    // =========================================================================
    // 3. EXTRACTION RULES (RESPONSE_BODY, RESPONSE_HEADER, URL_QUERY, REGEX)
    // =========================================================================

    @Test
    void testExtractionRulesFromJsonBodyAndResponseHeader() {
        RequestStep step1 = new RequestStep("Step 1: Login", "POST", "https://api.local/auth/login", "UNAUTHENTICATED");
        step1.setBodyTemplate("{\"user\": \"doc\", \"pass\": \"secret\"}");

        // Rule 1: Extract JWT from response body JSONPath
        ExtractionRule rule1 = new ExtractionRule("JWT_TOKEN", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.data.auth.token");
        // Rule 2: Extract nested account ID
        ExtractionRule rule2 = new ExtractionRule("ACCOUNT_ID", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.data.accounts[0].id");
        // Rule 3: Extract custom header from response
        ExtractionRule rule3 = new ExtractionRule("SESSION_NONCE", ExtractionSource.RESPONSE_HEADER, ExtractionType.EXACT_HEADER, "X-Auth-Nonce");
        // Rule 4: Fallback default value when property does not exist
        ExtractionRule rule4 = new ExtractionRule("FALLBACK_VAL", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.nonexistent.field");
        rule4.setDefaultValue("DEFAULT-123");

        step1.addExtraction(rule1);
        step1.addExtraction(rule2);
        step1.addExtraction(rule3);
        step1.addExtraction(rule4);

        mockHttpResponses.put("https://api.local/auth/login", req -> {
            String jsonResp = "{\n" +
                    "  \"status\": \"ok\",\n" +
                    "  \"data\": {\n" +
                    "    \"auth\": {\"token\": \"eyJhbGciOi...TOKEN99\"},\n" +
                    "    \"accounts\": [{\"id\": \"ACC-101\"}, {\"id\": \"ACC-102\"}]\n" +
                    "  }\n" +
                    "}";
            return createMockResponse(200, jsonResp, Map.of("X-Auth-Nonce", "nonce-xyz-777"));
        });

        Pipeline pipeline = new Pipeline("Extraction Test", "Tests extractions");
        pipeline.addStep(step1);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), false);

        assertTrue(result.isCompleted());
        Map<String, String> vars = result.getResolvedVariables();
        assertEquals("eyJhbGciOi...TOKEN99", vars.get("JWT_TOKEN"));
        assertEquals("ACC-101", vars.get("ACCOUNT_ID"));
        assertEquals("nonce-xyz-777", vars.get("SESSION_NONCE"));
        assertEquals("DEFAULT-123", vars.get("FALLBACK_VAL"));
    }

    @Test
    void testExtractionRulesWithRegexAndUrlQueryParam() {
        RequestStep step = new RequestStep("Step 1: Request with Query", "GET", "https://api.local/verify?callback=https://app/auth&session_id=SESS_4040", "UNAUTHENTICATED");
        step.setBodyTemplate("{\"raw\": \"transaction_id=TX-88321;status=approved\"}");

        // Extract from URL query param
        ExtractionRule qRule = new ExtractionRule("EXTRACTED_SESS", ExtractionSource.URL_QUERY_PARAM, ExtractionType.EXACT_HEADER, "session_id");
        // Extract from Request body using Regex
        ExtractionRule rRule = new ExtractionRule("TX_ID", ExtractionSource.REQUEST_BODY, ExtractionType.REGEX, "transaction_id=([A-Z0-9_-]+);");

        step.addExtraction(qRule);
        step.addExtraction(rRule);

        mockHttpResponses.put("https://api.local/verify?callback=https://app/auth&session_id=SESS_4040", req ->
                createMockResponse(200, "{\"verified\": true}", Collections.emptyMap()));

        Pipeline pipeline = new Pipeline("Regex & Query Test", "Test regex and query extraction");
        pipeline.addStep(step);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), false);

        assertTrue(result.isCompleted());
        assertEquals("SESS_4040", result.getResolvedVariables().get("EXTRACTED_SESS"));
        assertEquals("TX-88321", result.getResolvedVariables().get("TX_ID"));
    }

    // =========================================================================
    // 4. CHAIN RULES & CONDITIONAL BRANCHING
    // =========================================================================

    @Test
    void testConditionBranchStatusCodeAndAbort() {
        RequestStep step1 = new RequestStep("Step 1: Request Failing", "POST", "https://api.local/create", "UNAUTHENTICATED");
        mockHttpResponses.put("https://api.local/create", req -> createMockResponse(500, "{\"error\": \"Server Crash\"}", Collections.emptyMap()));

        ConditionBranch branch = new ConditionBranch("Check Step 1 Success",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                "200",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE);
        branch.setFailureNote("Creation failed with 500");

        RequestStep step2 = new RequestStep("Step 2: Should Not Run", "GET", "https://api.local/view", "UNAUTHENTICATED");

        Pipeline pipeline = new Pipeline("Abort Pipeline Test", "Tests STOP_PIPELINE");
        pipeline.addStep(step1);
        pipeline.addStep(branch);
        pipeline.addStep(step2);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), false);

        assertTrue(result.isCompleted());
        // Step 2 must NOT have run
        assertEquals(2, result.getStepRecords().size());
        assertEquals("Step 1: Request Failing", result.getStepRecords().get(0).getStepName());
        assertEquals("Check Step 1 Success", result.getStepRecords().get(1).getStepName());
        assertFalse(result.getStepRecords().get(1).isSuccess());
        assertTrue(result.getExecutionTrace().contains("Pipeline aborted: Creation failed with 500"));
        assertEquals(1, recordedRequests.size());
    }

    @Test
    void testConditionBranchBodyContainsDynamicVariable() {
        Map<String, String> vars = new HashMap<>();
        vars.put("SENSITIVE_KEY", "SECRET_KEY_998877");

        RequestStep step1 = new RequestStep("Step 1: Probe Endpoint", "GET", "https://api.local/leak", "UNAUTHENTICATED");
        mockHttpResponses.put("https://api.local/leak", req ->
                createMockResponse(200, "{\"msg\": \"Config dump\", \"key\": \"SECRET_KEY_998877\"}", Collections.emptyMap()));

        ConditionBranch branch = new ConditionBranch("Check Secret Leaked",
                ConditionBranch.ConditionType.BODY_CONTAINS,
                "{{SENSITIVE_KEY}}",
                ConditionBranch.BranchAction.MARK_VERDICT_BYPASS,
                ConditionBranch.BranchAction.MARK_VERDICT_ENFORCED);

        Pipeline pipeline = new Pipeline("Leak Detection Test", "Tests body contains {{VAR}}");
        pipeline.setInitialVariables(vars);
        pipeline.addStep(step1);
        pipeline.addStep(branch);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), false);

        assertTrue(result.isCompleted());
        assertEquals(Verdict.BYPASS, result.getFinalVerdict());
        assertTrue(result.getStepRecords().get(1).isSuccess());
    }

    // =========================================================================
    // 5. DEMONSTRATION: FULL STATEFUL BOLA ATTACK SCENARIO
    // =========================================================================

    @Test
    void testFullStatefulBolaAttackScenario() {
        // Setup 2 Roles: Doctor A (Victim) and Attacker B (Malicious User)
        UserRole victimRole = new UserRole("Doctor A (Victim)", "Authorization: Bearer doc_token_111", "");
        victimRole.setId("VICTIM_ROLE");

        UserRole attackerRole = new UserRole("Attacker B", "Authorization: Bearer attacker_token_222", "");
        attackerRole.setId("ATTACKER_ROLE");

        List<UserRole> roles = List.of(victimRole, attackerRole);

        // Step 1: Doctor creates a private patient record
        RequestStep createStep = new RequestStep("Step 1: Doctor Creates Record", "POST", "https://clinic.local/api/records", "VICTIM_ROLE");
        createStep.setBodyTemplate("{\"patient_name\": \"Classified Patient\", \"diagnosis\": \"Top Secret Condition\"}");
        // Extract the generated ID
        ExtractionRule extractId = new ExtractionRule("PATIENT_ID", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.record_id");
        createStep.addExtraction(extractId);

        mockHttpResponses.put("https://clinic.local/api/records", req -> {
            assertEquals("Bearer doc_token_111", req.header("Authorization").value());
            return createMockResponse(201, "{\"record_id\": \"REC-99401\", \"status\": \"created\"}", Collections.emptyMap());
        });

        // Step 2: Validate Step 1 succeeded before continuing
        ConditionBranch checkCreation = new ConditionBranch("Check Record Created",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                "201",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE);

        // Step 3: Attacker attempts to read Doctor's patient record using extracted {{PATIENT_ID}}
        RequestStep attackerAccessStep = new RequestStep("Step 3: Attacker Queries Patient", "GET", "https://clinic.local/api/records/{{PATIENT_ID}}", "ATTACKER_ROLE");

        // Attacker accesses victim's record and server mistakenly returns 200 (BOLA vulnerability!)
        mockHttpResponses.put("https://clinic.local/api/records/REC-99401", req -> {
            assertEquals("Bearer attacker_token_222", req.header("Authorization").value());
            return createMockResponse(200, "{\"record_id\": \"REC-99401\", \"patient_name\": \"Classified Patient\"}", Collections.emptyMap());
        });

        // Step 4: Final Assertion - If Attacker gets 200, mark as BYPASS (Vulnerable to BOLA), else ENFORCED
        ConditionBranch assertBola = new ConditionBranch("Assert BOLA Authorization Enforcement",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                "200",
                ConditionBranch.BranchAction.MARK_VERDICT_BYPASS,
                ConditionBranch.BranchAction.MARK_VERDICT_ENFORCED);

        Pipeline pipeline = new Pipeline("BOLA Cross-User Data Access Chain", "Demonstrates multi-step BOLA test");
        pipeline.addStep(createStep);
        pipeline.addStep(checkCreation);
        pipeline.addStep(attackerAccessStep);
        pipeline.addStep(assertBola);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, roles, false);

        assertTrue(result.isCompleted());
        assertEquals("REC-99401", result.getResolvedVariables().get("PATIENT_ID"));
        assertEquals(Verdict.BYPASS, result.getFinalVerdict(), "Pipeline should report BYPASS verdict for BOLA!");
        assertEquals(4, result.getStepRecords().size());
        assertEquals(2, recordedRequests.size());
        assertTrue(result.getExecutionTrace().contains("THORIZATION PIPELINE EXECUTION"));
        assertTrue(result.getExecutionTrace().contains("REC-99401"));
    }

    // =========================================================================
    // 6. TESTING EDGE CASES, FAILURE MODES & IDENTIFYING DEFECTS
    // =========================================================================

    @Test
    void testEdgeCase_VariableNotEmpty_WithDoubleCurlyBraces() {
        // If a user types {{VAR_NAME}} instead of VAR_NAME in the UI for VARIABLE_NOT_EMPTY
        Map<String, String> vars = new HashMap<>();
        vars.put("USER_ID", "12345");

        ConditionBranch branch1 = new ConditionBranch("Check with plain name",
                ConditionBranch.ConditionType.VARIABLE_NOT_EMPTY,
                "USER_ID",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE);

        ConditionBranch branch2 = new ConditionBranch("Check with curly braces",
                ConditionBranch.ConditionType.VARIABLE_NOT_EMPTY,
                "{{USER_ID}}",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE);

        Pipeline p1 = new Pipeline("P1", "");
        p1.setInitialVariables(vars);
        p1.addStep(branch1);

        Pipeline p2 = new Pipeline("P2", "");
        p2.setInitialVariables(vars);
        p2.addStep(branch2);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());

        PipelineExecutionResult res1 = executor.execute(p1, Collections.emptyList(), false);
        assertTrue(res1.getStepRecords().get(0).isSuccess(), "Plain VAR_NAME should evaluate to true when variable exists");

        PipelineExecutionResult res2 = executor.execute(p2, Collections.emptyList(), false);
        assertTrue(res2.getStepRecords().get(0).isSuccess(), "{{USER_ID}} with curly braces should also evaluate to true");
    }

    @Test
    void testEdgeCase_HeaderExtractionWithRegex() {
        RequestStep step = new RequestStep("Step 1", "GET", "https://api.local/get-cookie", "UNAUTHENTICATED");
        // User wants to extract only the session token from Set-Cookie header using regex (Syntax C: across headers)
        ExtractionRule rule1 = new ExtractionRule("SESS_TOKEN", ExtractionSource.RESPONSE_HEADER, ExtractionType.REGEX, "session_id=([a-zA-Z0-9]+)");
        // Syntax A: Header-Name: pattern
        ExtractionRule rule2 = new ExtractionRule("COOKIE_VAL", ExtractionSource.RESPONSE_HEADER, ExtractionType.REGEX, "Set-Cookie: session_id=([a-zA-Z0-9]+)");
        step.addExtraction(rule1);
        step.addExtraction(rule2);

        mockHttpResponses.put("https://api.local/get-cookie", req ->
                createMockResponse(200, "{}", Map.of("Set-Cookie", "session_id=tok123456; Path=/; HttpOnly")));

        Pipeline pipeline = new Pipeline("Header Regex Test", "");
        pipeline.addStep(step);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), false);

        String extracted1 = result.getResolvedVariables().get("SESS_TOKEN");
        String extracted2 = result.getResolvedVariables().get("COOKIE_VAL");
        assertEquals("tok123456", extracted1, "Syntax C: Regex across header lines should capture token");
        assertEquals("tok123456", extracted2, "Syntax A: Header: Regex should capture token");
    }

    @Test
    void testEdgeCase_DisabledStepSkipped() {
        RequestStep step1 = new RequestStep("Step 1", "GET", "https://api.local/run", "UNAUTHENTICATED");
        step1.setEnabled(false);

        mockHttpResponses.put("https://api.local/run", req -> createMockResponse(200, "{}", Collections.emptyMap()));

        Pipeline pipeline = new Pipeline("Disabled Step Test", "");
        pipeline.addStep(step1);

        PipelineExecutor executor = new PipelineExecutor(mockApi, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, Collections.emptyList(), false);

        assertTrue(result.isCompleted());
        assertEquals(0, result.getStepRecords().size());
        assertEquals(0, recordedRequests.size());
    }

    // =========================================================================
    // TEST HELPER: MONTOYA MOCK FACTORY & STUBS
    // =========================================================================

    private static MontoyaObjectFactory createMockObjectFactory() {
        return (MontoyaObjectFactory) Proxy.newProxyInstance(
                MontoyaObjectFactory.class.getClassLoader(),
                new Class<?>[]{MontoyaObjectFactory.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("httpRequestFromUrl".equals(name) && args != null && args.length >= 1) {
                        return createMockHttpRequest((String) args[0], "GET", "", new LinkedHashMap<>());
                    }
                    if ("byteArray".equals(name) && args != null && args.length >= 1) {
                        if (args[0] instanceof String) {
                            return createMockByteArray(((String) args[0]).getBytes(StandardCharsets.UTF_8));
                        } else if (args[0] instanceof byte[]) {
                            return createMockByteArray((byte[]) args[0]);
                        }
                    }
                    if ("httpHeader".equals(name) && args != null && args.length >= 2) {
                        return createMockHttpHeader((String) args[0], (String) args[1]);
                    }
                    return null;
                }
        );
    }

    private static HttpRequest createMockHttpRequest(String url, String method, String body, Map<String, String> headers) {
        final Map<String, String> currentHeaders = new LinkedHashMap<>(headers);
        final String[] currentMethod = new String[]{method != null ? method : "GET"};
        final String[] currentBody = new String[]{body != null ? body : ""};

        return (HttpRequest) Proxy.newProxyInstance(
                HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method m, Object[] args) {
                        String name = m.getName();
                        switch (name) {
                            case "url": return url;
                            case "method": return currentMethod[0];
                            case "withMethod":
                                currentMethod[0] = (String) args[0];
                                return proxy;
                            case "bodyToString": return currentBody[0];
                            case "body": return createMockByteArray(currentBody[0].getBytes(StandardCharsets.UTF_8));
                            case "withBody":
                                if (args[0] instanceof String) {
                                    currentBody[0] = (String) args[0];
                                } else if (args[0] instanceof ByteArray) {
                                    currentBody[0] = ((ByteArray) args[0]).toString();
                                }
                                return proxy;
                            case "hasHeader":
                                if (args[0] instanceof String) {
                                    String target = (String) args[0];
                                    for (String k : currentHeaders.keySet()) {
                                        if (k.equalsIgnoreCase(target)) return true;
                                    }
                                    return false;
                                }
                                return false;
                            case "header":
                                String target = (String) args[0];
                                for (Map.Entry<String, String> e : currentHeaders.entrySet()) {
                                    if (e.getKey().equalsIgnoreCase(target)) {
                                        return createMockHttpHeader(e.getKey(), e.getValue());
                                    }
                                }
                                return null;
                            case "headers":
                                List<HttpHeader> list = new ArrayList<>();
                                for (Map.Entry<String, String> e : currentHeaders.entrySet()) {
                                    list.add(createMockHttpHeader(e.getKey(), e.getValue()));
                                }
                                return list;
                            case "withAddedHeader":
                                if (args.length == 2) {
                                    currentHeaders.put((String) args[0], (String) args[1]);
                                } else if (args[0] instanceof HttpHeader) {
                                    HttpHeader h = (HttpHeader) args[0];
                                    currentHeaders.put(h.name(), h.value());
                                }
                                return proxy;
                            case "withRemovedHeader":
                                String toRemove = args[0] instanceof String ? (String) args[0] : ((HttpHeader) args[0]).name();
                                currentHeaders.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase(toRemove));
                                return proxy;
                            default: return null;
                        }
                    }
                }
        );
    }

    private static HttpResponse createMockResponse(int statusCode, String body, Map<String, String> headers) {
        return (HttpResponse) Proxy.newProxyInstance(
                HttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    switch (name) {
                        case "statusCode": return (short) statusCode;
                        case "bodyToString": return body != null ? body : "";
                        case "body": return createMockByteArray((body != null ? body : "").getBytes(StandardCharsets.UTF_8));
                        case "header":
                            String target = (String) args[0];
                            for (Map.Entry<String, String> e : headers.entrySet()) {
                                if (e.getKey().equalsIgnoreCase(target)) {
                                    return createMockHttpHeader(e.getKey(), e.getValue());
                                }
                            }
                            return null;
                        case "headers":
                            List<HttpHeader> list = new ArrayList<>();
                            for (Map.Entry<String, String> e : headers.entrySet()) {
                                list.add(createMockHttpHeader(e.getKey(), e.getValue()));
                            }
                            return list;
                        default: return null;
                    }
                }
        );
    }

    private static HttpRequestResponse createMockHttpRequestResponse(HttpRequest req, HttpResponse resp) {
        return (HttpRequestResponse) Proxy.newProxyInstance(
                HttpRequestResponse.class.getClassLoader(),
                new Class<?>[]{HttpRequestResponse.class},
                (proxy, method, args) -> {
                    if ("request".equals(method.getName())) return req;
                    if ("response".equals(method.getName())) return resp;
                    return null;
                }
        );
    }

    private static HttpHeader createMockHttpHeader(String name, String value) {
        return (HttpHeader) Proxy.newProxyInstance(
                HttpHeader.class.getClassLoader(),
                new Class<?>[]{HttpHeader.class},
                (proxy, method, args) -> {
                    if ("name".equals(method.getName())) return name;
                    if ("value".equals(method.getName())) return value;
                    return null;
                }
        );
    }

    private static ByteArray createMockByteArray(byte[] bytes) {
        return (ByteArray) Proxy.newProxyInstance(
                ByteArray.class.getClassLoader(),
                new Class<?>[]{ByteArray.class},
                (proxy, method, args) -> {
                    if ("length".equals(method.getName())) return bytes.length;
                    if ("getBytes".equals(method.getName())) return bytes;
                    if ("toString".equals(method.getName())) return new String(bytes, StandardCharsets.UTF_8);
                    return null;
                }
        );
    }
}
