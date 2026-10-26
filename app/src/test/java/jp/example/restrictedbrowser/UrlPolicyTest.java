package jp.example.restrictedbrowser;
import org.junit.Test;
import static org.junit.Assert.*;

public class UrlPolicyTest {
    @Test public void sameHostAndEffectivePortOnly() {
        UrlPolicy p = new UrlPolicy("https://order.example.com/start");
        assertTrue(p.allows("https://ORDER.example.com:443/menu?q=1#x"));
        for (String u : new String[]{"https://example.com", "https://other.order.example.com", "https://order.example.com.evil.com", "https://order.example.com:8443", "http://order.example.com", "https://order.example.com@evil.com", "https://order.example.com./"}) assertFalse(u, p.allows(u));
    }
    @Test public void strictEntryAndNoSearchFallback() {
        assertEquals("https://shop.example.com/a", UrlPolicy.fromInput("shop.example.com/a"));
        for (String s : new String[]{"カレー 店", "hello", "https://", "intent://shop.example.com", "javascript:alert(1)", "file:///a", "data:text/html,hi", "https://user@shop.example.com", "https://shop.example.com:0", "https://shop.example.com:65536", "https://shop.example.com:", "https://127.0.0.1", "https://[::1]", "https://shop.example.com\\@evil.com", "https://shop.example.com/a\nb", "https://shop%2eexample.com", "https://shop.example.com:0443", "https://shop.example.com/\tpath"}) {
            try { UrlPolicy.fromInput(s); fail(s); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void searchDomainsAtEntryFromEitherSource() {
        for (String h : new String[]{"google.com", "www.google.co.jp", "images.google.com", "google.co.uk", "google.com.hk", "google.cat", "bing.com", "www.bing.com", "duckduckgo.com", "search.yahoo.co.jp", "baidu.com", "yandex.ru", "search.brave.com", "ecosia.org", "kagi.com"}) {
            try { UrlPolicy.fromInput("https://" + h + "/"); fail(h); } catch (IllegalArgumentException expected) { }
        }
        assertEquals("https://notgoogle.example.com", UrlPolicy.fromInput("https://notgoogle.example.com"));
        assertEquals("https://bing.com.example.com", UrlPolicy.fromInput("https://bing.com.example.com"));
    }
    @Test public void everyRedirectUsesSamePolicy() {
        UrlPolicy p = new UrlPolicy("https://shop.example.com/a");
        assertEquals("https://shop.example.com/b", p.resolveRedirect("https://shop.example.com/a", "/b"));
        for (String dest : new String[]{"//evil.com/", "https://sub.shop.example.com/", "https://shop.example.com:8443/", "http://shop.example.com/", "intent://x", "//shop.example.com@evil.com/"}) {
            try { p.resolveRedirect("https://shop.example.com/a", dest); fail(dest); } catch (IllegalArgumentException expected) { }
        }
    }
}
