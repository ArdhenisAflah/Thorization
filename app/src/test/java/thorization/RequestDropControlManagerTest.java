package thorization;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.ResponseAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.proxy.MessageReceivedAction;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thorization.core.EngineController;
import thorization.core.HttpTrafficHandler;
import thorization.model.AuditRecord;
import thorization.model.FilterConfig;
import thorization.model.InterceptionMode;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.ui.RolesPanel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Unit Test suite for Request & Drop Control Manager and Regex Filtering sub-features:
 * 1. URL Include Regex & URL Exclude Regex (substring, anchored, case-insensitive, invalid regex resilience).
 * 2. Static file detection & ignore filters.
 * 3. State-changing HTTP methods and drop target decision logic (State-Changing vs Drop ALL).
 * 4. Interception modes with dummy traffic (Drop Original & Replay, Replay First Then Allow, Pass Through).
 * 5. Proxy message deduplication and Repeater tool separation.
 * 6. UI Synchronization in RolesPanel (ensuring (USP 1) text is removed and settings sync cleanly).
 */
class RequestDropControlManagerTest {

    private static MontoyaObjectFactory originalFactory;
    private MontoyaApi mockApi;
    private EngineController engineController;
    private HttpTrafficHandler trafficHandler;
    private final List<AuditRecord> submittedAuditRecords = Collections.synchronizedList(new ArrayList<>());

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
        submittedAuditRecords.clear();

        Http mockHttp = (Http) Proxy.newProxyInstance(
                Http.class.getClassLoader(),
                new Class<?>[]{Http.class},
                (proxy, method, args) -> null
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

        engineController = new EngineController(mockApi) {
            @Override
            public void submitAuditTask(HttpRequestResponse transaction, OriginalAction originalAction, boolean holdOriginal, String sourceTool) {
                AuditRecord rec = new AuditRecord(System.currentTimeMillis(), transaction);
                rec.setOriginalAction(originalAction);
                rec.setSourceTool(sourceTool);
                if (transaction != null && transaction.request() != null) {
                    rec.setMethod(transaction.request().method());
                    rec.setUrl(transaction.request().url());
                }
                submittedAuditRecords.add(rec);
            }
        };

        engineController.setEnabled(true);
        trafficHandler = new HttpTrafficHandler(engineController);
    }

    // =========================================================================
    // 1. REGEX FILTERING TESTS (INCLUDE & EXCLUDE WITH DUMMY DATA)
    // =========================================================================

    @Test
    void testUrlIncludeRegexFiltering() {
        FilterConfig config = new FilterConfig();

        // Dummy data endpoints
        String patientApiUrl = "https://hospital.org/api/v1/patients/10293";
        String doctorApiUrl = "https://hospital.org/api/v2/doctors/44";
        String authLoginUrl = "https://hospital.org/auth/login";
        String staticDocsUrl = "https://hospital.org/public/docs";

        // 1.1 Match versioned APIs with substring regex
        config.setUrlIncludeRegex("/api/v[12]/");
        assertTrue(config.matchesUrl(patientApiUrl), "Should match /api/v1/ in URL");
        assertTrue(config.matchesUrl(doctorApiUrl), "Should match /api/v2/ in URL");
        assertFalse(config.matchesUrl(authLoginUrl), "Should not match /auth/login");
        assertFalse(config.matchesUrl(staticDocsUrl), "Should not match /public/docs");

        // 1.2 Numeric ID pattern match
        config.setUrlIncludeRegex(".*/patients/[0-9]+$");
        assertTrue(config.matchesUrl(patientApiUrl), "Should match /patients/ followed by numeric ID");
        assertFalse(config.matchesUrl("https://hospital.org/api/v1/patients/new"), "Should not match non-numeric endpoint");

        // 1.3 Case insensitivity check
        config.setUrlIncludeRegex("DOCTORS");
        assertTrue(config.matchesUrl(doctorApiUrl), "Should match case-insensitively");

        // 1.4 Invalid regex syntax handling (must not throw or crash)
        config.setUrlIncludeRegex("[unclosed-regex-bracket(");
        assertFalse(config.matchesUrl(patientApiUrl), "Malformed regex must fail safe without throwing exception");

        // 1.5 Empty / whitespace include regex (allows all valid URLs)
        config.setUrlIncludeRegex("   ");
        assertTrue(config.matchesUrl(patientApiUrl), "Blank include regex should match any URL");
        assertTrue(config.matchesUrl(authLoginUrl), "Blank include regex should match any URL");
    }

    @Test
    void testUrlExcludeRegexFiltering() {
        FilterConfig config = new FilterConfig();

        String healthCheckUrl = "https://service.internal/health";
        String logoutUrl = "https://service.internal/api/v1/logout";
        String metricsUrl = "https://service.internal/metrics?format=prometheus";
        String userProfileUrl = "https://service.internal/api/v1/user/profile";

        // Exclude telemetry & session termination endpoints
        config.setUrlExcludeRegex("/(health|logout|metrics)");
        assertFalse(config.matchesUrl(healthCheckUrl), "Should exclude /health endpoint");
        assertFalse(config.matchesUrl(logoutUrl), "Should exclude /logout endpoint");
        assertFalse(config.matchesUrl(metricsUrl), "Should exclude /metrics endpoint");
        assertTrue(config.matchesUrl(userProfileUrl), "Should allow standard user profile endpoint");

        // Exclude specific file extensions via regex
        config.setUrlExcludeRegex(".*\\.(pdf|docx|xlsx)($|\\?.*)");
        assertFalse(config.matchesUrl("https://service.internal/files/invoice.pdf"), "Should exclude PDF files");
        assertFalse(config.matchesUrl("https://service.internal/export.xlsx?token=xyz"), "Should exclude XLSX with query param");
        assertTrue(config.matchesUrl("https://service.internal/files/view?id=123"), "Should allow view endpoint");
    }

    @Test
    void testCombinedIncludeAndExcludeRegex() {
        FilterConfig config = new FilterConfig();

        // User wants only /api/ traffic, but excludes /admin/ and /health
        config.setUrlIncludeRegex("/api/");
        config.setUrlExcludeRegex("/(admin|health)");

        String normalApi = "https://portal.com/api/v1/cart/items";
        String adminApi = "https://portal.com/api/v1/admin/manage-roles";
        String publicHome = "https://portal.com/home";

        assertTrue(config.matchesUrl(normalApi), "Normal API matches include and passes exclude");
        assertFalse(config.matchesUrl(adminApi), "Admin API matches include but is excluded by exclude regex");
        assertFalse(config.matchesUrl(publicHome), "Public home does not match include regex");
    }

    // =========================================================================
    // 2. STATIC FILE FILTERING TESTS
    // =========================================================================

    @Test
    void testStaticFileFilteringWithDummyUrls() {
        FilterConfig config = new FilterConfig();
        assertTrue(config.isIgnoreStaticFiles());

        List<String> staticUrls = List.of(
                "https://cdn.example.com/assets/app.js?v=2.0.1",
                "https://cdn.example.com/styles/theme.dark.css",
                "https://cdn.example.com/img/logo.PNG",
                "https://cdn.example.com/img/photo.jpeg#thumb",
                "https://cdn.example.com/fonts/inter.woff2",
                "https://cdn.example.com/icons/favicon.ico",
                "https://cdn.example.com/media/intro.mp4",
                "https://cdn.example.com/scripts/vendor.js.map"
        );

        List<String> dynamicApiUrls = List.of(
                "https://api.example.com/users",
                "https://api.example.com/items.json",
                "https://api.example.com/download?format=png",
                "https://api.example.com/graphql"
        );

        for (String url : staticUrls) {
            assertTrue(config.isStaticUrl(url), "Expected static URL: " + url);
            assertFalse(config.matchesUrl(url), "Static URL should be ignored by matchesUrl: " + url);
        }

        for (String url : dynamicApiUrls) {
            assertFalse(config.isStaticUrl(url), "Expected dynamic API URL: " + url);
            assertTrue(config.matchesUrl(url), "Dynamic URL should be accepted by matchesUrl: " + url);
        }

        // When ignoreStaticFiles is turned OFF, static URLs are accepted
        config.setIgnoreStaticFiles(false);
        for (String url : staticUrls) {
            assertTrue(config.matchesUrl(url), "When ignoreStaticFiles is false, static URL should match: " + url);
        }
    }

    // =========================================================================
    // 3. STATE-CHANGING METHODS & DROP TARGET DECISIONS
    // =========================================================================

    @Test
    void testStateChangingMethodsAndDropTargetDecisions() {
        FilterConfig config = new FilterConfig();

        // 3.1 Defaults: POST, PUT, DELETE, PATCH are state-changing
        assertTrue(config.isStateChanging("POST"));
        assertTrue(config.isStateChanging("PUT"));
        assertTrue(config.isStateChanging("DELETE"));
        assertTrue(config.isStateChanging("PATCH"));
        assertFalse(config.isStateChanging("GET"));
        assertFalse(config.isStateChanging("OPTIONS"));
        assertFalse(config.isStateChanging("HEAD"));

        // 3.2 Drop Target in Standard (State-Changing Only) mode
        assertFalse(config.isDropAllRequests());
        assertTrue(config.isDropTarget("POST"));
        assertTrue(config.isDropTarget("DELETE"));
        assertFalse(config.isDropTarget("GET"));
        assertFalse(config.isDropTarget("OPTIONS"));

        // 3.3 Drop Target in Aggressive (Drop ALL Requests) mode
        config.setDropAllRequests(true);
        assertTrue(config.isDropTarget("GET"), "GET should be targeted when dropAllRequests is true");
        assertTrue(config.isDropTarget("OPTIONS"), "OPTIONS should be targeted when dropAllRequests is true");
        assertTrue(config.isDropTarget("POST"), "POST should still be targeted");

        // 3.4 Customizing state-changing methods
        config.setDropAllRequests(false);
        Set<String> customMethods = new HashSet<>(Arrays.asList("POST", "CUSTOM_ACTION"));
        config.setStateChangingMethods(customMethods);

        assertTrue(config.isDropTarget("CUSTOM_ACTION"));
        assertFalse(config.isDropTarget("DELETE"), "DELETE was removed from state-changing methods");
        assertFalse(config.isDropTarget("PATCH"), "PATCH was removed from state-changing methods");
    }

    // =========================================================================
    // 4. INTERCEPTION MODES & DUMMY PROXY TRAFFIC HANDLING
    // =========================================================================

    @Test
    void testProxyDropOriginalAndReplayMode() {
        // Set mode to Drop Original & Replay
        engineController.getFilterConfig().setInterceptionMode(InterceptionMode.DROP_ORIGINAL_AND_REPLAY);
        engineController.getFilterConfig().setDropAllRequests(false);

        // Dummy State-Changing Request (POST /api/transfer)
        InterceptedRequest postRequest = createMockInterceptedRequest(
                1001,
                "POST",
                "https://bank.local/api/transfer",
                "/api/transfer",
                "{\"amount\": 5000, \"recipient\": \"attacker\"}"
        );

        ProxyRequestReceivedAction postAction = trafficHandler.handleRequestReceived(postRequest);

        // Assert native Burp Proxy drop action was triggered
        assertNotNull(postAction);
        assertEquals(MessageReceivedAction.DROP, postAction.action(), "POST request must be dropped in Proxy");
        assertEquals(1, submittedAuditRecords.size());
        AuditRecord record = submittedAuditRecords.get(0);
        assertEquals(OriginalAction.DROPPED, record.getOriginalAction());
        assertEquals("Proxy", record.getSourceTool());
        assertEquals("POST", record.getMethod());

        // Dummy Non-State-Changing Request (GET /api/balance)
        InterceptedRequest getRequest = createMockInterceptedRequest(
                1002,
                "GET",
                "https://bank.local/api/balance",
                "/api/balance",
                ""
        );

        ProxyRequestReceivedAction getAction = trafficHandler.handleRequestReceived(getRequest);
        assertNotNull(getAction);
        assertEquals(MessageReceivedAction.CONTINUE, getAction.action(), "GET request must NOT be dropped");
        assertEquals(1, submittedAuditRecords.size(), "GET should pass through to server without drop audit task");
    }

    @Test
    void testProxyReplayFirstThenAllowMode() {
        engineController.getFilterConfig().setInterceptionMode(InterceptionMode.REPLAY_FIRST_THEN_ALLOW);

        InterceptedRequest putRequest = createMockInterceptedRequest(
                2001,
                "PUT",
                "https://api.store.local/user/address",
                "/user/address",
                "{\"street\": \"123 Cyber Way\"}"
        );

        ProxyRequestReceivedAction action = trafficHandler.handleRequestReceived(putRequest);

        // Request must NOT be dropped, but held then passed
        assertNotNull(action);
        assertEquals(MessageReceivedAction.CONTINUE, action.action(), "Request should continue (be allowed)");
        assertEquals(1, submittedAuditRecords.size());
        AuditRecord rec = submittedAuditRecords.get(0);
        assertEquals(OriginalAction.HELD_THEN_PASSED, rec.getOriginalAction());
        assertEquals("Proxy", rec.getSourceTool());
    }

    @Test
    void testProxyPassThroughMode() {
        engineController.getFilterConfig().setInterceptionMode(InterceptionMode.PASS_THROUGH_AND_REPLAY);

        InterceptedRequest deleteRequest = createMockInterceptedRequest(
                3001,
                "DELETE",
                "https://api.store.local/user/orders/88",
                "/user/orders/88",
                ""
        );

        ProxyRequestReceivedAction action = trafficHandler.handleRequestReceived(deleteRequest);
        assertNotNull(action);
        assertEquals(MessageReceivedAction.CONTINUE, action.action(), "In Pass Through mode, proxy must not drop");
        assertEquals(0, submittedAuditRecords.size(), "ProxyRequestReceivedAction does not submit in Pass Through mode (waits for response)");
    }

    @Test
    void testEngineDisabledPassesAllTrafficUntouched() {
        engineController.setEnabled(false);
        engineController.getFilterConfig().setInterceptionMode(InterceptionMode.DROP_ORIGINAL_AND_REPLAY);

        InterceptedRequest destructiveReq = createMockInterceptedRequest(
                4001,
                "DELETE",
                "https://corp.local/api/users/all",
                "/api/users/all",
                ""
        );

        ProxyRequestReceivedAction action = trafficHandler.handleRequestReceived(destructiveReq);
        assertNotNull(action);
        assertEquals(MessageReceivedAction.CONTINUE, action.action(), "When engine is disabled, requests must not be dropped");
        assertEquals(0, submittedAuditRecords.size());
    }

    // =========================================================================
    // 5. REPEATER TOOL SEPARATION & PROXY DEDUPLICATION
    // =========================================================================

    @Test
    void testRepeaterTrafficHandlingAndDeduplication() {
        engineController.getFilterConfig().setInterceptionMode(InterceptionMode.DROP_ORIGINAL_AND_REPLAY);

        // 5.1 Repeater traffic via HttpResponseReceived
        HttpRequest req = createMockHttpRequest("https://api.target.local/api/profile", "GET", "", Map.of());
        HttpResponse resp = createMockHttpResponse(200, "{\"username\":\"admin\"}", Map.of());
        ToolSource repeaterTool = createMockToolSource(ToolType.REPEATER);

        HttpResponseReceived repeaterResponse = createMockHttpResponseReceived(5001, req, resp, repeaterTool);
        ResponseReceivedAction action = trafficHandler.handleHttpResponseReceived(repeaterResponse);

        assertNotNull(action);
        assertEquals(1, submittedAuditRecords.size());
        AuditRecord rec = submittedAuditRecords.get(0);
        assertEquals("Repeater", rec.getSourceTool());
        assertEquals(OriginalAction.PASSED, rec.getOriginalAction());

        // 5.2 Deduplication: If a message was already handled in Proxy, it must be ignored in response handler
        InterceptedRequest proxyReq = createMockInterceptedRequest(
                6001,
                "POST",
                "https://api.target.local/api/action",
                "/api/action",
                "{\"run\":true}"
        );
        trafficHandler.handleRequestReceived(proxyReq);
        assertEquals(2, submittedAuditRecords.size()); // 1 from Repeater + 1 from Proxy drop

        // Simulate Proxy response firing with same messageId 6001
        ToolSource proxyTool = createMockToolSource(ToolType.PROXY);
        HttpResponseReceived proxyResponse = createMockHttpResponseReceived(6001, proxyReq, resp, proxyTool);
        trafficHandler.handleHttpResponseReceived(proxyResponse);

        // Total should STILL be 2, because messageId 6001 was already handled and deduplicated
        assertEquals(2, submittedAuditRecords.size(), "Deduplication must prevent double processing of message 6001");
    }

    // =========================================================================
    // 6. UI SYNCHRONIZATION & (USP 1) TEXT REMOVAL VERIFICATION
    // =========================================================================

    @Test
    void testRolesPanelSettingsSynchronizationAndUspTextRemoval() {
        RolesPanel panel = new RolesPanel(engineController);
        assertNotNull(panel);

        // Traverse components to verify "(USP 1)" is NOT present anywhere in text, borders, or tooltips
        List<String> discoveredTexts = new ArrayList<>();
        collectAllUiTexts(panel, discoveredTexts);

        for (String txt : discoveredTexts) {
            assertFalse(txt.contains("USP 1"), "UI must not contain unprofessional '(USP 1)' label: " + txt);
            assertFalse(txt.contains("(USP 1)"), "UI must not contain unprofessional '(USP 1)' label: " + txt);
        }

        // Test synchronization: Change FilterConfig via controller and verify UI reflects it
        FilterConfig newConfig = new FilterConfig();
        newConfig.setInterceptionMode(InterceptionMode.DROP_ORIGINAL_AND_REPLAY);
        newConfig.setDropAllRequests(true);
        newConfig.setUrlIncludeRegex("/api/v[3-5]/");
        newConfig.setUrlExcludeRegex("/internal/");
        newConfig.setStateChangingMethods(Set.of("POST", "PUT"));

        engineController.setFilterConfig(newConfig);

        assertEquals(InterceptionMode.DROP_ORIGINAL_AND_REPLAY, engineController.getFilterConfig().getInterceptionMode());
        assertTrue(engineController.getFilterConfig().isDropAllRequests());
        assertEquals("/api/v[3-5]/", engineController.getFilterConfig().getUrlIncludeRegex());
        assertEquals("/internal/", engineController.getFilterConfig().getUrlExcludeRegex());
    }

    private void collectAllUiTexts(Component comp, List<String> texts) {
        if (comp == null) return;

        if (comp instanceof JLabel) {
            texts.add(((JLabel) comp).getText());
        } else if (comp instanceof AbstractButton) {
            texts.add(((AbstractButton) comp).getText());
        }

        if (comp instanceof JComponent) {
            JComponent jc = (JComponent) comp;
            if (jc.getToolTipText() != null) {
                texts.add(jc.getToolTipText());
            }
            if (jc.getBorder() != null) {
                extractBorderTitles(jc.getBorder(), texts);
            }
        }

        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                collectAllUiTexts(child, texts);
            }
        }
    }

    private void extractBorderTitles(javax.swing.border.Border border, List<String> texts) {
        if (border instanceof TitledBorder) {
            texts.add(((TitledBorder) border).getTitle());
        } else if (border instanceof CompoundBorder) {
            CompoundBorder cb = (CompoundBorder) border;
            extractBorderTitles(cb.getOutsideBorder(), texts);
            extractBorderTitles(cb.getInsideBorder(), texts);
        }
    }

    // =========================================================================
    // MOCK GENERATION HELPERS
    // =========================================================================

    private static InterceptedRequest createMockInterceptedRequest(int messageId, String method, String url, String path, String body) {
        return (InterceptedRequest) Proxy.newProxyInstance(
                InterceptedRequest.class.getClassLoader(),
                new Class<?>[]{InterceptedRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    switch (name) {
                        case "messageId": return messageId;
                        case "method": return method;
                        case "url": return url;
                        case "path": return path;
                        case "bodyToString": return body != null ? body : "";
                        case "body": return createMockByteArray((body != null ? body : "").getBytes(StandardCharsets.UTF_8));
                        case "headers": return Collections.emptyList();
                        case "isInScope": return true;
                        case "toString": return method + " " + url;
                        default: return null;
                    }
                }
        );
    }

    private static HttpResponseReceived createMockHttpResponseReceived(int messageId, HttpRequest req, HttpResponse resp, ToolSource tool) {
        return (HttpResponseReceived) Proxy.newProxyInstance(
                HttpResponseReceived.class.getClassLoader(),
                new Class<?>[]{HttpResponseReceived.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    switch (name) {
                        case "messageId": return messageId;
                        case "initiatingRequest": return req;
                        case "toolSource": return tool;
                        case "statusCode": return resp.statusCode();
                        case "bodyToString": return resp.bodyToString();
                        case "body": return resp.body();
                        case "headers": return resp.headers();
                        default: return null;
                    }
                }
        );
    }

    private static ToolSource createMockToolSource(ToolType type) {
        return (ToolSource) Proxy.newProxyInstance(
                ToolSource.class.getClassLoader(),
                new Class<?>[]{ToolSource.class},
                (proxy, m, args) -> {
                    if ("toolType".equals(m.getName())) return type;
                    if ("isFromTool".equals(m.getName()) && args != null && args.length > 0) {
                        return type == args[0];
                    }
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
                    if ("httpRequestResponse".equals(name) && args != null && args.length >= 2) {
                        return createMockHttpRequestResponse((HttpRequest) args[0], (HttpResponse) args[1]);
                    }
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
                    if ("requestInitialInterceptResultDrop".equals(name)) {
                        return createMockProxyRequestReceivedAction(MessageReceivedAction.DROP, null);
                    }
                    if ("requestInitialInterceptResultFollowUserRules".equals(name) && args != null && args.length >= 1) {
                        return createMockProxyRequestReceivedAction(MessageReceivedAction.CONTINUE, (HttpRequest) args[0]);
                    }
                    if ("responseResult".equals(name) && args != null && args.length >= 1) {
                        return createMockResponseReceivedAction((HttpResponse) args[0]);
                    }
                    return null;
                }
        );
    }

    private static ProxyRequestReceivedAction createMockProxyRequestReceivedAction(MessageReceivedAction action, HttpRequest request) {
        return (ProxyRequestReceivedAction) Proxy.newProxyInstance(
                ProxyRequestReceivedAction.class.getClassLoader(),
                new Class<?>[]{ProxyRequestReceivedAction.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("action".equals(name)) return action;
                    if ("request".equals(name)) return request;
                    if ("toString".equals(name)) return action != null ? action.name() : "PROXY_ACTION";
                    return null;
                }
        );
    }

    private static ResponseReceivedAction createMockResponseReceivedAction(HttpResponse response) {
        return (ResponseReceivedAction) Proxy.newProxyInstance(
                ResponseReceivedAction.class.getClassLoader(),
                new Class<?>[]{ResponseReceivedAction.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("response".equals(name)) return response;
                    if ("action".equals(name)) return ResponseAction.CONTINUE;
                    if ("toString".equals(name)) return "CONTINUE";
                    return null;
                }
        );
    }

    private static HttpRequest createMockHttpRequest(String url, String method, String body, Map<String, String> headers) {
        return (HttpRequest) Proxy.newProxyInstance(
                HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    switch (name) {
                        case "url": return url;
                        case "method": return method;
                        case "bodyToString": return body != null ? body : "";
                        case "body": return createMockByteArray((body != null ? body : "").getBytes(StandardCharsets.UTF_8));
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

    private static HttpResponse createMockHttpResponse(int statusCode, String body, Map<String, String> headers) {
        return (HttpResponse) Proxy.newProxyInstance(
                HttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    switch (name) {
                        case "statusCode": return (short) statusCode;
                        case "bodyToString": return body != null ? body : "";
                        case "body": return createMockByteArray((body != null ? body : "").getBytes(StandardCharsets.UTF_8));
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
                (proxy, m, args) -> {
                    if ("request".equals(m.getName())) return req;
                    if ("response".equals(m.getName())) return resp;
                    return null;
                }
        );
    }

    private static HttpHeader createMockHttpHeader(String name, String value) {
        return (HttpHeader) Proxy.newProxyInstance(
                HttpHeader.class.getClassLoader(),
                new Class<?>[]{HttpHeader.class},
                (proxy, m, args) -> {
                    if ("name".equals(m.getName())) return name;
                    if ("value".equals(m.getName())) return value;
                    return null;
                }
        );
    }

    private static ByteArray createMockByteArray(byte[] bytes) {
        return (ByteArray) Proxy.newProxyInstance(
                ByteArray.class.getClassLoader(),
                new Class<?>[]{ByteArray.class},
                (proxy, m, args) -> {
                    if ("length".equals(m.getName())) return bytes.length;
                    if ("getBytes".equals(m.getName())) return bytes;
                    if ("toString".equals(m.getName())) return new String(bytes, StandardCharsets.UTF_8);
                    return null;
                }
        );
    }
}
