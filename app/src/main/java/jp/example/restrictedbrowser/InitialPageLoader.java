package jp.example.restrictedbrowser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/** One native GET preparation, before a pinned BrowserSession or any page script exists. */
final class InitialPageLoader {
    private final String url;
    private final NavigationGateway.Cookies cookies;
    private final String userAgent;
    private final NavigationGateway.Connector connector;
    private final AtomicBoolean attempted = new AtomicBoolean();
    private final Object lifecycle = new Object();
    private boolean cancelled;

    InitialPageLoader(String url, NavigationGateway.Cookies cookies, String userAgent) {
        this(url, cookies, userAgent, target -> (HttpURLConnection) URI.create(target).toURL().openConnection());
    }
    InitialPageLoader(String url, NavigationGateway.Cookies cookies, String userAgent,
            NavigationGateway.Connector connector) {
        UrlPolicy.parse(url);
        this.url = url; this.cookies = cookies; this.userAgent = userAgent; this.connector = connector;
    }
    NavigationGateway.Page load() throws IOException {
        if (!attempted.compareAndSet(false, true)) throw new IOException("初回のページ取得は既に開始しています。");
        ensureActive();
        NavigationGateway.Cookies scopedCookies = new NavigationGateway.Cookies() {
            public String get(String target) {
                synchronized (lifecycle) {
                    if (cancelled) throw new IllegalStateException("ページ取得は終了しました。");
                    return cookies.get(target);
                }
            }
            public void put(String target, String value) {
                synchronized (lifecycle) {
                    if (!cancelled) cookies.put(target, value);
                }
            }
        };
        NavigationGateway.Page page = NavigationGateway.fetchInitial(url, scopedCookies, userAgent, target -> {
            ensureActive();
            return connector.open(target);
        }, this::ensureActive);
        ensureActive();
        return page;
    }
    void cancel() {
        // Wait for any app-supplied Cookie mutation before the caller clears the session's Cookies.
        synchronized (lifecycle) { cancelled = true; }
    }
    private void ensureActive() throws IOException {
        synchronized (lifecycle) {
            if (cancelled) throw new IOException("ページ取得は終了しました。");
        }
    }
}
