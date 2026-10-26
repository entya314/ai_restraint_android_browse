package jp.example.restrictedbrowser;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NavigationGatewayTest {
    static class Reply extends HttpURLConnection {
        int code; String location; boolean auto; ByteArrayOutputStream sent = new ByteArrayOutputStream();
        String methodSeen;
        Reply(int code, String location) throws Exception { super(new URL("https://shop.example.com/")); this.code=code; this.location=location; }
        public void connect() {} public void disconnect() {} public boolean usingProxy() { return false; }
        @Override public void setInstanceFollowRedirects(boolean b) { auto=b; }
        @Override public int getResponseCode() { methodSeen=getRequestMethod(); return code; }
        @Override public String getHeaderField(String key) { return key.equalsIgnoreCase("Location") ? location : null; }
        @Override public Map<String,List<String>> getHeaderFields() { return Map.of("Content-Type",List.of("text/html; charset=UTF-8"),"Set-Cookie",List.of("session=ok; Secure"),"Content-Security-Policy",List.of("script-src 'self'")); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream("<html>ok</html>".getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getOutputStream() { return sent; }
    }
    static class Fixture {
        List<String> opened = new ArrayList<>(); List<String> cookies = new ArrayList<>(); Deque<Reply> replies = new ArrayDeque<>();
        NavigationGateway gateway = new NavigationGateway(new UrlPolicy("https://shop.example.com"), new NavigationGateway.Cookies() {
            public String get(String url) { return "session=before"; }
            public void put(String url,String value) { cookies.add(value); }
        }, "test", u -> { opened.add(u); return replies.remove(); });
    }
    @Test public void directCrossOriginNeverConnects() throws Exception {
        Fixture f = new Fixture();
        try { f.gateway.fetch("https://evil.com", "GET", null,null,null); fail(); } catch (IOException expected) {}
        assertTrue(f.opened.isEmpty());
    }
    @Test public void sameOriginGetRedirectAndSecurityHeaders() throws Exception {
        Fixture f = new Fixture(); Reply first=new Reply(302,"/done"), last=new Reply(200,null); f.replies.add(first); f.replies.add(last);
        NavigationGateway.Page page=f.gateway.fetch("https://shop.example.com/a","GET",null,null,null);
        assertEquals("https://shop.example.com/done",page.url); assertFalse(first.auto); assertFalse(last.auto);
        assertTrue(page.headers.get("content-security-policy").contains("script-src 'self', form-action https://shop.example.com"));
        assertEquals("no-store",page.headers.get("cache-control")); assertEquals(2,f.cookies.size());
    }
    @Test public void postExternalRedirectIsStoppedBeforeDestinationForAllRedirectCodes() throws Exception {
        for (int code : new int[]{301,302,303,307,308}) {
            Fixture f = new Fixture(); Reply first=new Reply(code,"https://payments.example.net/charge"); f.replies.add(first);
            try { f.gateway.fetch("https://shop.example.com/order","POST","name=abc".getBytes(),"application/x-www-form-urlencoded",null); fail(); } catch (IOException expected) {}
            assertEquals(1,f.opened.size()); assertEquals("name=abc",first.sent.toString()); assertFalse(first.auto);
        }
    }
    @Test public void post307PreservesBodyAnd303BecomesGet() throws Exception {
        for (int code : new int[]{303,307,308}) {
            Fixture f=new Fixture(); Reply a=new Reply(code,"/next"), b=new Reply(200,null); f.replies.add(a); f.replies.add(b);
            f.gateway.fetch("https://shop.example.com/order","POST","people=2".getBytes(),"application/x-www-form-urlencoded","https://shop.example.com/menu");
            assertEquals(code==303?"GET":"POST",b.methodSeen);
            assertEquals(code==303?"":"people=2",b.sent.toString());
        }
    }
    @Test public void redirectChainCannotEscapeAfterAnAllowedHop() throws Exception {
        Fixture f=new Fixture(); f.replies.add(new Reply(302,"/allowed")); f.replies.add(new Reply(302,"https://sub.shop.example.com/"));
        try { f.gateway.fetch("https://shop.example.com/a","GET",null,null,null); fail(); } catch (IOException expected) {}
        assertEquals(List.of("https://shop.example.com/a","https://shop.example.com/allowed"),f.opened);
    }
    @Test public void redirectLoopIsBounded() throws Exception {
        Fixture f=new Fixture(); for(int i=0;i<11;i++) f.replies.add(new Reply(302,"/loop"));
        try { f.gateway.fetch("https://shop.example.com/loop","GET",null,null,null); fail(); } catch (IOException expected) {}
        assertEquals(11,f.opened.size());
    }
}
