package thorization.substitution;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight dynamic proxy test harness for Montoya HttpRequest and HttpService.
 * Enables zero-dependency, headless JUnit 5 execution without live Burp Suite.
 */
public final class MockHttpRequestHelper {

    private MockHttpRequestHelper() {
        throw new UnsupportedOperationException("Test helper");
    }

    public static HttpRequest createMockRequest(String url, String method, String body, Map<String, String> initialHeaders) {
        final Map<String, String> headers = new LinkedHashMap<>();
        if (initialHeaders != null) {
            headers.putAll(initialHeaders);
        }

        HttpService mockService = (HttpService) Proxy.newProxyInstance(
                HttpService.class.getClassLoader(),
                new Class<?>[]{HttpService.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    if ("host".equals(name)) return extractHostFromUrl(url);
                    if ("port".equals(name)) return url.startsWith("https") ? 443 : 80;
                    if ("secure".equals(name)) return url.startsWith("https");
                    return null;
                }
        );

        return (HttpRequest) Proxy.newProxyInstance(
                HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class},
                (proxy, m, args) -> {
                    String name = m.getName();
                    switch (name) {
                        case "url": return url;
                        case "method": return method;
                        case "path": return extractPathFromUrl(url);
                        case "pathWithoutQuery": return extractPathWithoutQueryFromUrl(url);
                        case "query": return extractQueryFromUrl(url);
                        case "httpService": return mockService;
                        case "bodyToString": return body != null ? body : "";
                        case "body": return createMockByteArray((body != null ? body : "").getBytes(StandardCharsets.UTF_8));
                        case "headerValue":
                            if (args != null && args.length > 0 && args[0] != null) {
                                String search = args[0].toString().toLowerCase();
                                for (Map.Entry<String, String> e : headers.entrySet()) {
                                    if (e.getKey().equalsIgnoreCase(search)) return e.getValue();
                                }
                            }
                            return null;
                        case "hasHeader":
                            if (args != null && args.length > 0 && args[0] != null) {
                                String search = args[0].toString().toLowerCase();
                                for (String h : headers.keySet()) {
                                    if (h.equalsIgnoreCase(search)) return true;
                                }
                            }
                            return false;
                        case "headers":
                            List<HttpHeader> list = new ArrayList<>();
                            for (Map.Entry<String, String> e : headers.entrySet()) {
                                list.add(createMockHeader(e.getKey(), e.getValue()));
                            }
                            return list;
                        case "withPath":
                            if (args != null && args.length > 0) {
                                String newPath = (String) args[0];
                                String newUrl = replacePathInUrl(url, newPath);
                                return createMockRequest(newUrl, method, body, headers);
                            }
                            return proxy;
                        case "withBody":
                            if (args != null && args.length > 0) {
                                String newBody = (String) args[0];
                                return createMockRequest(url, method, newBody, headers);
                            }
                            return proxy;
                        case "withUpdatedHeader":
                            if (args != null && args.length >= 2) {
                                Map<String, String> newHeaders = new LinkedHashMap<>(headers);
                                String headerName = (String) args[0];
                                String headerVal = (String) args[1];
                                newHeaders.put(headerName, headerVal);
                                return createMockRequest(url, method, body, newHeaders);
                            }
                            return proxy;
                        case "withAddedHeader":
                            if (args != null && args.length >= 2) {
                                Map<String, String> newHeaders = new LinkedHashMap<>(headers);
                                String headerName = (String) args[0];
                                String headerVal = (String) args[1];
                                newHeaders.put(headerName, headerVal);
                                return createMockRequest(url, method, body, newHeaders);
                            }
                            return proxy;
                        case "withRemovedHeader":
                            if (args != null && args.length >= 1) {
                                Map<String, String> newHeaders = new LinkedHashMap<>(headers);
                                String toRemove = (String) args[0];
                                newHeaders.keySet().removeIf(k -> k.equalsIgnoreCase(toRemove));
                                return createMockRequest(url, method, body, newHeaders);
                            }
                            return proxy;
                        case "toString":
                            return method + " " + url;
                        default:
                            return null;
                    }
                }
        );
    }

    private static String extractHostFromUrl(String url) {
        int slashIdx = url.indexOf("://");
        if (slashIdx != -1) {
            int hostStart = slashIdx + 3;
            int hostEnd = url.indexOf('/', hostStart);
            if (hostEnd == -1) hostEnd = url.length();
            String hostAndPort = url.substring(hostStart, hostEnd);
            int colon = hostAndPort.indexOf(':');
            return colon != -1 ? hostAndPort.substring(0, colon) : hostAndPort;
        }
        return "localhost";
    }

    private static String extractPathFromUrl(String url) {
        int slashIdx = url.indexOf("://");
        if (slashIdx != -1) {
            int pathStart = url.indexOf('/', slashIdx + 3);
            return pathStart != -1 ? url.substring(pathStart) : "/";
        }
        return url;
    }

    private static String extractPathWithoutQueryFromUrl(String url) {
        String path = extractPathFromUrl(url);
        int q = path.indexOf('?');
        return q != -1 ? path.substring(0, q) : path;
    }

    private static String extractQueryFromUrl(String url) {
        int q = url.indexOf('?');
        if (q != -1) {
            int hash = url.indexOf('#', q);
            return hash != -1 ? url.substring(q + 1, hash) : url.substring(q + 1);
        }
        return null;
    }

    private static String replacePathInUrl(String url, String newPath) {
        int slashIdx = url.indexOf("://");
        if (slashIdx != -1) {
            int pathStart = url.indexOf('/', slashIdx + 3);
            String origin = pathStart != -1 ? url.substring(0, pathStart) : url;
            return origin + (newPath.startsWith("/") ? newPath : "/" + newPath);
        }
        return newPath;
    }

    private static HttpHeader createMockHeader(String name, String value) {
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
