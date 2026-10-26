package jp.example.restrictedbrowser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns document requests, including POST. No automatic redirects, custom TLS or certificate overrides. */
public final class NavigationGateway {
    public interface Cookies { String get(String url); void put(String url, String value); }
    public interface Connector { HttpURLConnection open(String url) throws IOException; }
    public static final class Page {
        public final String url;
        public final int status;
        public final Map<String, String> headers;
        public final byte[] body;
        Page(String url, int status, Map<String, String> headers, byte[] body) {
            this.url = url; this.status = status; this.headers = headers; this.body = body;
        }
    }
    private final UrlPolicy policy;
    private final Cookies cookies;
    private final Connector connector;
    private final String userAgent;
    public NavigationGateway(UrlPolicy policy, Cookies cookies, String userAgent) {
        this(policy, cookies, userAgent, url -> (HttpURLConnection) URI.create(url).toURL().openConnection());
    }
    NavigationGateway(UrlPolicy policy, Cookies cookies, String userAgent, Connector connector) {
        this.policy = policy; this.cookies = cookies; this.userAgent = userAgent; this.connector = connector;
    }
    public Page fetch(String initial, String method, byte[] body, String contentType, String referer) throws IOException {
        String url = initial;
        if (!(method.equals("GET") || method.equals("POST"))) throw new IOException("未対応の送信方法です。");
        for (int hop = 0; hop < 11; hop++) {
            if (!policy.allows(url)) throw new IOException("別ドメインへの移動を禁止しました。");
            HttpURLConnection c = connector.open(url);
            try {
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(15000); c.setReadTimeout(20000);
                c.setRequestMethod(method);
                c.setRequestProperty("User-Agent", userAgent);
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
                c.setRequestProperty("Accept-Language", "ja,en;q=0.5");
                c.setRequestProperty("Accept-Encoding", "identity");
                c.setRequestProperty("Cache-Control", "no-cache");
                String cookie = cookies.get(url);
                if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
                if (referer != null && policy.allows(referer)) c.setRequestProperty("Referer", referer);
                if (method.equals("POST")) {
                    c.setRequestProperty("Origin", policy.origin);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", contentType);
                    c.setFixedLengthStreamingMode(body.length);
                    try (var out = c.getOutputStream()) { out.write(body); }
                }
                int status = c.getResponseCode();
                Map<String, String> headers = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                    if (e.getKey() == null) continue;
                    if (e.getKey().equalsIgnoreCase("Set-Cookie")) {
                        for (String v : e.getValue()) cookies.put(url, v);
                    } else if (!List.of("content-length", "transfer-encoding", "connection", "content-encoding")
                            .contains(e.getKey().toLowerCase(Locale.ROOT))) {
                        headers.put(e.getKey().toLowerCase(Locale.ROOT), String.join(", ", e.getValue()));
                    }
                }
                if (List.of(301, 302, 303, 307, 308).contains(status)) {
                    if (hop == 10) throw new IOException("転送回数が多すぎます。");
                    try { url = policy.resolveRedirect(url, c.getHeaderField("Location")); }
                    catch (IllegalArgumentException e) { throw new IOException(e.getMessage()); }
                    if (status == 303 || ((status == 301 || status == 302) && method.equals("POST"))) {
                        method = "GET"; body = null; contentType = null;
                    }
                    continue;
                }
                if (status < 200 || status >= 600 || status == 204 || status == 205)
                    throw new IOException("この応答は表示できません（HTTP " + status + "）。");
                String type = headers.getOrDefault("content-type", "").toLowerCase(Locale.ROOT);
                if (!(type.startsWith("text/html") || type.startsWith("application/xhtml+xml")) ||
                        headers.getOrDefault("content-disposition", "").toLowerCase(Locale.ROOT).contains("attachment"))
                    throw new IOException("HTMLページ以外の表示・ダウンロードには対応していません。");
                if (headers.containsKey("content-encoding") ||
                        (c.getContentEncoding() != null && !c.getContentEncoding().equalsIgnoreCase("identity")))
                    throw new IOException("未対応の圧縮形式です。");
                // Enforced as a response header, before any author script; original CSP remains enforced too.
                String extraCsp = "form-action " + policy.origin + "; frame-src " + policy.origin +
                        "; object-src 'none'; base-uri " + policy.origin;
                headers.merge("content-security-policy", extraCsp, (original, extra) -> original + ", " + extra);
                headers.put("cache-control", "no-store");
                headers.remove("refresh"); // Never honor server Refresh without navigation validation.
                InputStream input = status >= 400 ? c.getErrorStream() : c.getInputStream();
                if (input == null) throw new IOException("応答本文がありません。");
                byte[] bytes;
                try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (output.size() + n > 8 * 1024 * 1024) throw new IOException("ページが大きすぎます。");
                        output.write(buffer, 0, n);
                    }
                    bytes = output.toByteArray();
                }
                return new Page(url, status, headers, bytes);
            } finally { c.disconnect(); }
        }
        throw new IOException("転送を中断しました。");
    }
}
