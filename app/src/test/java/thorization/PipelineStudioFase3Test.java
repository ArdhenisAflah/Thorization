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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thorization.core.EngineController;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineStep;
import thorization.model.pipeline.RequestStep;
import thorization.ui.PipelineStudioPanel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class PipelineStudioFase3Test {

    @BeforeAll
    static void setup() {
        System.setProperty("java.awt.headless", "true");
        ObjectFactoryLocator.FACTORY = createMockObjectFactory();
    }

    @Test
    void testPipelineExecutionRoleTrafficMapping() throws Exception {
        UserRole doctorRole = new UserRole("Doctor A", "Authorization: Bearer doc_123", "");
        doctorRole.setId("DOC_ROLE");

        UserRole attackerRole = new UserRole("Attacker B", "Authorization: Bearer atk_456", "");
        attackerRole.setId("ATK_ROLE");

        List<UserRole> roles = List.of(doctorRole, attackerRole);

        // Setup Mock HTTP Server Responses
        Map<String, HttpResponse> mockResponses = new LinkedHashMap<>();
        mockResponses.put("https://api.test.local/records",
                createMockResponse(201, "{\"id\": \"REC-991\"}", Map.of("Content-Type", "application/json")));
        mockResponses.put("https://api.test.local/records/REC-991",
                createMockResponse(200, "{\"id\": \"REC-991\", \"data\": \"Sensitive Medical Data\"}", Map.of("Content-Type", "application/json")));
        mockResponses.put("https://api.test.local/public/ping",
                createMockResponse(200, "{\"pong\": true}", Map.of("Content-Type", "application/json")));

        MontoyaApi mockApi = createMockMontoyaApi(mockResponses);
        EngineController controller = new EngineController(mockApi);
        controller.setRoles(roles);

        // Pipeline: Step 1 (Doctor), Step 2 (Attacker), Step 3 (Unauth), Step 4 (Assertion Branch)
        Pipeline pipeline = new Pipeline("BOLA Attack Chain", "Cross role test");

        RequestStep step1 = new RequestStep("Create Record", "POST", "https://api.test.local/records", "DOC_ROLE");
        step1.setBodyTemplate("{\"patient\": \"John Doe\"}");
        step1.addExtraction(new ExtractionRule("RECORD_ID", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.id"));

        RequestStep step2 = new RequestStep("Steal Record", "GET", "https://api.test.local/records/{{RECORD_ID}}", "ATK_ROLE");

        RequestStep step3 = new RequestStep("Unauth Ping", "GET", "https://api.test.local/public/ping", "UNAUTHENTICATED");

        ConditionBranch step4 = new ConditionBranch("Assert BOLA Leak",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS, "200",
                ConditionBranch.BranchAction.MARK_VERDICT_BYPASS, ConditionBranch.BranchAction.MARK_VERDICT_ENFORCED);

        pipeline.addStep(step1);
        pipeline.addStep(step2);
        pipeline.addStep(step3);
        pipeline.addStep(step4);

        CountDownLatch latch = new CountDownLatch(1);
        controller.executePipeline(pipeline, result -> latch.countDown());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Pipeline execution should complete");

        List<AuditRecord> records = controller.getRecords();
        assertEquals(1, records.size(), "One AuditRecord must be published to main table");

        AuditRecord record = records.get(0);
        assertTrue(record.isPipelineExecution());
        assertEquals("BOLA Attack Chain", record.getPipelineName());

        // Verify Doctor A mapping
        AuthorizationCheckResult docResult = record.getRoleResults().get("DOC_ROLE");
        assertNotNull(docResult, "Doctor role result must be populated");
        assertEquals(Verdict.BYPASS, docResult.getVerdict());
        assertEquals(201, docResult.getStatusCode());
        assertNotNull(docResult.getRequest(), "Doctor step request must be mapped");
        assertNotNull(docResult.getResponse(), "Doctor step response must be mapped");

        // Verify Attacker B mapping
        AuthorizationCheckResult atkResult = record.getRoleResults().get("ATK_ROLE");
        assertNotNull(atkResult, "Attacker role result must be populated");
        assertEquals(Verdict.BYPASS, atkResult.getVerdict());
        assertEquals(200, atkResult.getStatusCode());
        assertNotNull(atkResult.getRequest(), "Attacker step request must be mapped");
        assertNotNull(atkResult.getResponse(), "Attacker step response must be mapped");

        // Verify Unauthenticated mapping
        AuthorizationCheckResult unauthResult = record.getUnauthenticatedResult();
        assertNotNull(unauthResult, "Unauthenticated step result must be populated");
        assertEquals(200, unauthResult.getStatusCode());
        assertNotNull(unauthResult.getRequest(), "Unauthenticated request must be mapped");
        assertNotNull(unauthResult.getResponse(), "Unauthenticated response must be mapped");
    }

    @Test
    void testCreatePipelineFromRequestsCopiesHeadersAndBody() {
        MontoyaApi mockApi = createMockMontoyaApi(Collections.emptyMap());
        EngineController controller = new EngineController(mockApi);
        PipelineStudioPanel panel = new PipelineStudioPanel(mockApi, controller);

        Map<String, String> hdrs = new LinkedHashMap<>();
        hdrs.put("Host", "target.local");
        hdrs.put("Authorization", "Bearer original_token");
        hdrs.put("Content-Type", "application/json");
        hdrs.put("X-Custom-Tenant", "tenant-99");
        hdrs.put("Content-Length", "25");

        HttpRequest req = createMockHttpRequest("https://target.local/api/resource", "POST", "{\"action\": \"create\"}", hdrs);
        HttpResponse resp = createMockResponse(200, "{\"ok\": true}", Collections.emptyMap());
        HttpRequestResponse tx = createMockHttpRequestResponse(req, resp);

        AuditRecord auditRecord = new AuditRecord(1, tx);

        panel.createPipelineFromRequests(List.of(auditRecord));

        List<Pipeline> pipelines = controller.getPipelines();
        assertEquals(2, pipelines.size(), "Should have 2 pipelines: default sample + newly generated pipeline");
        Pipeline created = pipelines.get(1);
        assertEquals(1, created.getSteps().size());

        RequestStep step = (RequestStep) created.getSteps().get(0);
        assertEquals("POST", step.getMethod());
        assertEquals("https://target.local/api/resource", step.getUrlTemplate());
        assertEquals("{\"action\": \"create\"}", step.getBodyTemplate());

        String headersTemplate = step.getHeadersTemplate();
        assertNotNull(headersTemplate);
        assertTrue(headersTemplate.contains("Authorization: Bearer original_token"));
        assertTrue(headersTemplate.contains("Content-Type: application/json"));
        assertTrue(headersTemplate.contains("X-Custom-Tenant: tenant-99"));
        // Host and Content-Length should be omitted so Montoya calculates them dynamically
        assertFalse(headersTemplate.contains("Host: target.local"));
        assertFalse(headersTemplate.contains("Content-Length: 25"));
    }

    @Test
    void testDefaultSamplePipelineHasCompleteBolaAssertionFlow() {
        MontoyaApi mockApi = createMockMontoyaApi(Collections.emptyMap());
        EngineController controller = new EngineController(mockApi);

        // When PipelineStudioPanel is initialized with no pipelines, default sample is created
        PipelineStudioPanel panel = new PipelineStudioPanel(mockApi, controller);

        List<Pipeline> pipelines = controller.getPipelines();
        assertEquals(1, pipelines.size(), "Default sample pipeline must be created");

        Pipeline defaultPipe = pipelines.get(0);
        List<PipelineStep> steps = defaultPipe.getSteps();
        assertEquals(4, steps.size(), "Default scenario must have 4 steps for full BOLA verification");

        // Step 1: Create
        assertTrue(steps.get(0) instanceof RequestStep);
        RequestStep s1 = (RequestStep) steps.get(0);
        assertEquals("POST", s1.getMethod());
        assertEquals(1, s1.getExtractions().size());
        assertEquals("RESOURCE_ID", s1.getExtractions().get(0).getVariableName());

        // Step 2: Branch Check 200
        assertTrue(steps.get(1) instanceof ConditionBranch);
        ConditionBranch b1 = (ConditionBranch) steps.get(1);
        assertEquals(ConditionBranch.ConditionType.STATUS_CODE_EQUALS, b1.getConditionType());
        assertEquals("200", b1.getExpectedValue());
        assertEquals(ConditionBranch.BranchAction.CONTINUE, b1.getOnTrueAction());
        assertEquals(ConditionBranch.BranchAction.STOP_PIPELINE, b1.getOnFalseAction());

        // Step 3: Access Attacker
        assertTrue(steps.get(2) instanceof RequestStep);
        RequestStep s2 = (RequestStep) steps.get(2);
        assertEquals("GET", s2.getMethod());
        assertTrue(s2.getUrlTemplate().contains("{{RESOURCE_ID}}"));

        // Step 4: Assert BOLA Verdict
        assertTrue(steps.get(3) instanceof ConditionBranch);
        ConditionBranch b2 = (ConditionBranch) steps.get(3);
        assertEquals(ConditionBranch.ConditionType.STATUS_CODE_EQUALS, b2.getConditionType());
        assertEquals("200", b2.getExpectedValue());
        assertEquals(ConditionBranch.BranchAction.MARK_VERDICT_BYPASS, b2.getOnTrueAction());
        assertEquals(ConditionBranch.BranchAction.MARK_VERDICT_ENFORCED, b2.getOnFalseAction());
    }

    // =========================================================================
    // MOCK HELPERS
    // =========================================================================

    private static MontoyaApi createMockMontoyaApi(Map<String, HttpResponse> urlResponseMap) {
        Http http = (Http) Proxy.newProxyInstance(
                Http.class.getClassLoader(),
                new Class<?>[]{Http.class},
                (proxy, method, args) -> {
                    if ("sendRequest".equals(method.getName()) && args.length >= 1) {
                        HttpRequest req = (HttpRequest) args[0];
                        String url = req.url();
                        HttpResponse resp = urlResponseMap.get(url);
                        if (resp == null) {
                            resp = createMockResponse(404, "{\"error\": \"not found\"}", Collections.emptyMap());
                        }
                        return createMockHttpRequestResponse(req, resp);
                    }
                    return null;
                }
        );

        return (MontoyaApi) Proxy.newProxyInstance(
                MontoyaApi.class.getClassLoader(),
                new Class<?>[]{MontoyaApi.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("http".equals(name)) return http;
                    return null;
                }
        );
    }

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
