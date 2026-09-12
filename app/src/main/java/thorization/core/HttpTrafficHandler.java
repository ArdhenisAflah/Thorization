package thorization.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import thorization.model.FilterConfig;
import thorization.model.InterceptionMode;
import thorization.model.OriginalAction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts Burp Suite HTTP and Proxy traffic, enforcing Safe Request
 * Interception & Drop Control (USP 1) specifically for Proxy traffic (browser/mobile),
 * while capturing Repeater traffic as standard audited pass-through.
 */
public class HttpTrafficHandler implements HttpHandler, ProxyRequestHandler {

    private final EngineController engineController;
    private final Set<Integer> proxyHandledMessageIds = ConcurrentHashMap.newKeySet();

    public HttpTrafficHandler(EngineController engineController) {
        this.engineController = engineController;
    }

    private void log(String msg) {
        MontoyaApi api = engineController.getApi();
        if (api != null && api.logging() != null) {
            api.logging().logToOutput("[Thorization] " + msg);
        }
    }

    // --- ProxyRequestHandler Implementation (Native Proxy Drop for Browser/Mobile Traffic) ---

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        if (!engineController.isEnabled()) {
            return ProxyRequestReceivedAction.continueWith(interceptedRequest);
        }

        FilterConfig config = engineController.getFilterConfig();
        String method = interceptedRequest.method();
        String url = interceptedRequest.url();

        if (!config.matchesUrl(url)) {
            return ProxyRequestReceivedAction.continueWith(interceptedRequest);
        }

        boolean isTarget = config.isDropTarget(method);
        InterceptionMode mode = config.getInterceptionMode();

        // 1. USP 1: Drop Original & Replay (Safe Mode in Proxy)
        if (isTarget && mode == InterceptionMode.DROP_ORIGINAL_AND_REPLAY) {
            proxyHandledMessageIds.add(interceptedRequest.messageId());
            log("[PROXY] DROPPING original request: " + method + " " + url + " (Testing attacker roles safely)");
            HttpRequestResponse transaction = HttpRequestResponse.httpRequestResponse(interceptedRequest, null);
            engineController.submitAuditTask(transaction, OriginalAction.DROPPED, false, "Proxy");
            return ProxyRequestReceivedAction.drop();
        }

        // 2. USP 1: Replay First, Then Allow Original
        if (isTarget && mode == InterceptionMode.REPLAY_FIRST_THEN_ALLOW) {
            proxyHandledMessageIds.add(interceptedRequest.messageId());
            log("[PROXY] REPLAYING FIRST (Holding original): " + method + " " + url);
            HttpRequestResponse transaction = HttpRequestResponse.httpRequestResponse(interceptedRequest, null);
            engineController.submitAuditTask(transaction, OriginalAction.HELD_THEN_PASSED, true, "Proxy");
            return ProxyRequestReceivedAction.continueWith(interceptedRequest);
        }

        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    // --- HttpHandler Implementation (for Repeater and general pass-through traffic) ---

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        if (!engineController.isEnabled()) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        int messageId = responseReceived.messageId();
        // If already dropped/handled in Proxy, do not re-process
        if (proxyHandledMessageIds.remove(messageId)) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        ToolType tool = responseReceived.toolSource().toolType();
        if (tool == ToolType.PROXY || tool == ToolType.REPEATER) {
            String toolName = tool == ToolType.REPEATER ? "Repeater" : "Proxy";
            String method = responseReceived.initiatingRequest().method();
            String url = responseReceived.initiatingRequest().url();

            log("[" + toolName.toUpperCase() + "] Captured response: " + method + " " + url + " (Status: " + responseReceived.statusCode() + ")");
            HttpRequestResponse httpTransaction = HttpRequestResponse.httpRequestResponse(
                    responseReceived.initiatingRequest(),
                    responseReceived
            );
            engineController.submitAuditTask(httpTransaction, OriginalAction.PASSED, false, toolName);
        }

        return ResponseReceivedAction.continueWith(responseReceived);
    }
}
