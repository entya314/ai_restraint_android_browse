package jp.example.restrictedbrowser;

import android.app.Activity;
import android.net.Uri;
import android.os.Message;
import android.webkit.*;
import android.widget.Toast;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** One origin and one WebView. Only this instance can consume its pending document response. */
public final class BrowserSession {
    private final Activity activity;
    public final RestrictedWebView web;
    private final UrlPolicy policy;
    private final NavigationGateway gateway;
    private final ExecutorService posts = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private volatile boolean posting;
    private volatile NavigationGateway.Page pending;
    private long lastNotice;

    public static boolean supported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS);
    }
    public BrowserSession(Activity activity, String firstUrl) throws IOException {
        this(activity, firstUrl, url -> (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection());
    }
    BrowserSession(Activity activity, String firstUrl, NavigationGateway.Connector connector) throws IOException {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS))
            throw new IOException("Android System WebViewを更新してください。");
        this.activity = activity;
        policy = new UrlPolicy(firstUrl);
        web = new RestrictedWebView(activity);
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true); s.setGeolocationEnabled(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false); s.setSupportMultipleWindows(true);
        s.setMediaPlaybackRequiresUserGesture(true); s.setSaveFormData(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(web, false);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            ServiceWorkerControllerCompat sw = ServiceWorkerControllerCompat.getInstance();
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS)) {
                sw.getServiceWorkerWebSettings().setBlockNetworkLoads(true);
            } else throw new IOException("Service Worker制御が利用できません。");
            sw.setServiceWorkerClient(new ServiceWorkerClientCompat() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest r) { return denied(); }
            });
        } else throw new IOException("Service Worker制御が利用できません。");
        gateway = new NavigationGateway(policy, new NavigationGateway.Cookies() {
            public String get(String url) { if (closed) throw new IllegalStateException("セッションは終了しました。"); return cm.getCookie(url); }
            public void put(String url, String value) { if (!closed) cm.setCookie(url, value); }
        }, s.getUserAgentString(), connector);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message msg) { notice("新しいウィンドウは禁止されています。"); return false; }
            @Override public void onPermissionRequest(PermissionRequest r) { r.deny(); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> result, FileChooserParams p) {
                result.onReceiveValue(null); notice("ファイル添付には対応していません。"); return true;
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) { cb.invoke(origin, false, false); }
        });
        web.setDownloadListener((u, ua, d, m, length) -> notice("ダウンロードは禁止されています。"));
        web.setOnLongClickListener(v -> {
            int t = web.getHitTestResult().getType();
            return t == WebView.HitTestResult.SRC_ANCHOR_TYPE || t == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                t == WebView.HitTestResult.IMAGE_TYPE || t == WebView.HitTestResult.PHONE_TYPE || t == WebView.HitTestResult.EMAIL_TYPE;
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!policy.allows(r.getUrl().toString())) { notice("別ドメイン・外部アプリへの移動は禁止されています。"); return true; }
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return !policy.allows(url); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (closed) return denied();
                if (!r.isForMainFrame()) return null; // CDN images, CSS, scripts and APIs are not document navigation.
                String url = r.getUrl().toString();
                if (!policy.allows(url) || !r.getMethod().equals("GET")) {
                    notice("この移動・送信は禁止されています。"); return denied();
                }
                try {
                    NavigationGateway.Page page = pending;
                    if (page != null && documentKey(page.url).equals(documentKey(url))) { pending = null; return response(page); }
                    page = gateway.fetch(url, "GET", null, null, r.getRequestHeaders().get("Referer"));
                    if (closed) return denied();
                    if (!documentKey(page.url).equals(documentKey(url))) {
                        deliver(page); return denied();
                    }
                    return response(page);
                } catch (Exception e) { notice("ページを開けません: " + e.getMessage()); return denied(); }
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                if (!policy.allows(url)) { v.stopLoading(); notice("許可されていない移動を停止しました。"); }
            }
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) {
                h.cancel(); notice("証明書を確認できないため接続を中止しました。");
            }
            @Override public void onSafeBrowsingHit(WebView v, WebResourceRequest r, int threat, SafeBrowsingResponse response) {
                response.backToSafety(true); notice("危険なサイトへの接続を中止しました。");
            }
            @Override public void onReceivedClientCertRequest(WebView v, ClientCertRequest request) { request.cancel(); }
            @Override public void onReceivedHttpAuthRequest(WebView v, HttpAuthHandler handler, String host, String realm) { handler.cancel(); }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                close(); activity.recreate(); return true;
            }
        });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(web, "RestrictedNavigation", Set.of(policy.origin),
            (view, message, sourceOrigin, isMainFrame, reply) -> {
                if (closed || !policy.allows(sourceOrigin.toString()) || !isMainFrame) return;
                try { onMessage(message.getData()); }
                catch (Exception e) { notice("送信できません: " + e.getMessage()); }
            });
        } else throw new IOException("安全なメッセージ機能が利用できません。");
        String script;
        try (InputStream in = activity.getAssets().open("guard.js")) {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, script.replace("__PINNED_ORIGIN__", JSONObject.quote(policy.origin)), Set.of(policy.origin));
        } else throw new IOException("文書開始時の制御が利用できません。");
    }
    public String domain() { return policy.host; }
    public void open(String url) { if (!closed && policy.allows(url)) web.loadUrl(url); }
    private void onMessage(String data) throws Exception {
        if (data == null || data.length() > 1024 * 1024) throw new IOException("送信内容が大きすぎます。");
        JSONObject m = new JSONObject(data);
        if (!m.optString("kind").equals("form")) { notice("この操作は禁止されています。"); return; }
        if (posting) { notice("送信中です。しばらくお待ちください。"); return; }
        String url = m.getString("url"), method = m.getString("method").toUpperCase(Locale.ROOT);
        if (!policy.allows(url) || !List.of("GET", "POST").contains(method)) throw new IOException("許可されていない送信先です。");
        String referer = m.optString("referer");
        JSONArray fields = m.getJSONArray("fields");
        String enctype = m.optString("enctype", "application/x-www-form-urlencoded");
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        String contentType;
        if (method.equals("POST") && enctype.equals("multipart/form-data")) {
            String boundary = "----RestrictedBrowser" + UUID.randomUUID().toString().replace("-", "");
            contentType = "multipart/form-data; boundary=" + boundary;
            for (int i = 0; i < fields.length(); i++) {
                JSONArray f = fields.getJSONArray(i);
                String key = f.getString(0).replace("\r", "%0D").replace("\n", "%0A").replace("\"", "%22");
                payload.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + key + "\"\r\n\r\n" + f.getString(1) + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            payload.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } else {
            boolean plain = method.equals("POST") && enctype.equals("text/plain");
            contentType = plain ? "text/plain; charset=UTF-8" : "application/x-www-form-urlencoded; charset=UTF-8";
            StringJoiner joined = new StringJoiner(plain ? "\r\n" : "&");
            for (int i = 0; i < fields.length(); i++) {
                JSONArray f = fields.getJSONArray(i);
                joined.add(plain ? f.getString(0) + "=" + f.getString(1) :
                    URLEncoder.encode(f.getString(0), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(f.getString(1), StandardCharsets.UTF_8));
            }
            payload.write((joined + (plain && fields.length() > 0 ? "\r\n" : "")).getBytes(StandardCharsets.UTF_8));
        }
        if (method.equals("GET")) {
            Uri target = Uri.parse(url).buildUpon().encodedQuery(payload.toString(StandardCharsets.UTF_8)).build();
            open(target.toString()); return;
        }
        posting = true;
        final byte[] bytes = payload.toByteArray();
        posts.execute(() -> {
            try { deliver(gateway.fetch(url, "POST", bytes, contentType, referer)); }
            catch (Exception e) { notice("注文の応答を確認できません: " + e.getMessage() + " 再送前に店舗へ確認してください。"); }
            finally { posting = false; }
        });
    }
    private void deliver(NavigationGateway.Page page) {
        activity.runOnUiThread(() -> {
            if (!closed) { pending = page; web.loadUrl(page.url); }
        });
    }
    private static String documentKey(String url) {
        java.net.URI u = java.net.URI.create(url);
        String path = u.getRawPath();
        return new UrlPolicy(url).origin + (path == null || path.isEmpty() ? "/" : path) +
            (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
    }
    private WebResourceResponse response(NavigationGateway.Page page) {
        String ct = page.headers.getOrDefault("content-type", "text/html; charset=UTF-8");
        String mime = ct.split(";", 2)[0];
        String encoding = "UTF-8";
        java.util.regex.Matcher charset = java.util.regex.Pattern.compile("charset\\s*=\\s*[\"']?([^;\\s\"']+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(ct);
        if (charset.find()) encoding = charset.group(1);
        return new WebResourceResponse(mime, encoding, page.status, "Document", page.headers, new ByteArrayInputStream(page.body));
    }
    private static WebResourceResponse denied() {
        byte[] bytes = "<!doctype html><html lang=ja><meta charset=utf-8><meta name=viewport content='width=device-width'><p>このページは表示できません。元の画面へ戻るか、終了してください。</p></html>".getBytes(StandardCharsets.UTF_8);
        return new WebResourceResponse("text/html", "UTF-8", 403, "Blocked", Map.of("Content-Security-Policy", "default-src 'none'", "Cache-Control", "no-store"), new ByteArrayInputStream(bytes));
    }
    private void notice(String message) {
        activity.runOnUiThread(() -> {
            if (closed) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastNotice < 1500) return;
            lastNotice = now; Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        });
    }
    public void close() {
        if (closed) return;
        closed = true; pending = null; posts.shutdownNow();
        web.stopLoading(); web.clearHistory(); web.clearCache(true); web.destroy();
        WebStorage.getInstance().deleteAllData();
    }
}
