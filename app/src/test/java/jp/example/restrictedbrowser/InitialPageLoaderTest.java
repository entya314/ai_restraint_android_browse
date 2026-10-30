package jp.example.restrictedbrowser;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.SSLHandshakeException;

/** Real redirect/policy code with deterministic HTTP replies; no live site or account is contacted. */
public class InitialPageLoaderTest {
    static final String ENTRY = "https://entry.example.com/start";
    static final String FINAL = "https://mypage.example.net/menu";
    static class Reply extends HttpURLConnection {
        final int status;
        final String location;
        boolean auto = true;
        String type = "text/html; charset=UTF-8";
        String html = "<!doctype html><p>ready</p>";
        Runnable onResponse = () -> {};
        Reply(int status, String location) throws Exception { super(new URL(ENTRY)); this.status=status; this.location=location; }
        public void connect() {} public void disconnect() {} public boolean usingProxy() { return false; }
        @Override public void setInstanceFollowRedirects(boolean follow) { auto=follow; }
        @Override public int getResponseCode() { onResponse.run(); return status; }
        @Override public String getHeaderField(String name) { return name.equalsIgnoreCase("Location") ? location : null; }
        @Override public Map<String,List<String>> getHeaderFields() {
            return Map.of("Content-Type",List.of(type),"Set-Cookie",List.of("session=response; Secure"),
                "Content-Security-Policy",List.of("script-src 'self'"));
        }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)); }
    }
    static class Fixture {
        final List<String> opened = new ArrayList<>(), stored = new ArrayList<>();
        final Deque<Reply> replies = new ArrayDeque<>();
        final NavigationGateway.Cookies cookies = new NavigationGateway.Cookies() {
            public String get(String url) { return url.startsWith("https://entry.example.com/") ? "entry=only" : "destination=only"; }
            public void put(String url,String value) { stored.add(url); }
        };
        final NavigationGateway.Connector connector = url -> { opened.add(url); return replies.removeFirst(); };
        InitialPageLoader loader() { return new InitialPageLoader(ENTRY,cookies,"test-agent",connector); }
    }
    static void fails(InitialPageLoader loader) throws Exception {
        try { loader.load(); fail("Expected initial preparation to be rejected"); } catch (IOException expected) { }
    }
    @Test public void directPageStillOpensOnce() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(200,null));
        assertEquals(ENTRY,f.loader().load().url);assertEquals(List.of(ENTRY),f.opened);
    }
    @Test public void allInitialRedirectCodesCanCrossHostsAsGet() throws Exception {
        for(int status:new int[]{301,302,303,307,308}) {
            Fixture f=new Fixture();Reply a=new Reply(status,FINAL),b=new Reply(200,null);f.replies.add(a);f.replies.add(b);
            assertEquals(FINAL,f.loader().load().url);assertEquals(List.of(ENTRY,FINAL),f.opened);
            assertEquals("GET",a.getRequestMethod());assertEquals("GET",b.getRequestMethod());
            assertFalse(a.auto);assertFalse(b.auto);
        }
    }
    @Test public void multiHopAndRelativeLocationUseTheCurrentUrl() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(301,"/moved"));
        f.replies.add(new Reply(302,"//mypage.example.net/area/a"));f.replies.add(new Reply(307,"../menu?q=1#part"));f.replies.add(new Reply(200,null));
        NavigationGateway.Page page=f.loader().load();
        assertEquals("https://mypage.example.net/menu?q=1#part",page.url);
        assertEquals(List.of(ENTRY,"https://entry.example.com/moved","https://mypage.example.net/area/a",page.url),f.opened);
    }
    @Test public void everyDestinationIsValidatedBeforeConnecting() throws Exception {
        for(String url:new String[]{"http://mypage.example.net/", "https://google.com/", "https://www.bing.com/", "https://sub.duckduckgo.com/",
                "intent://launch", "javascript:alert(1)", "data:text/html,hi", "file:///local", "content://local",
                "https://user@mypage.example.net/", "https://mypage.example.net:0/", "https://mypage.example.net:65536/",
                "https://127.0.0.1/", "https://mypage.example.net\\@evil.example.org/", "https://mypage.example.net./"}) {
            Fixture f=new Fixture();f.replies.add(new Reply(302,url));fails(f.loader());assertEquals(url,1,f.opened.size());
        }
    }
    @Test public void invalidEntryNeverConnects() throws Exception {
        Fixture f=new Fixture();
        for(String url:new String[]{"https://google.co.jp/", "http://entry.example.com/", "intent://x", "not-a-url"}) {
            try { new InitialPageLoader(url,f.cookies,"test",f.connector).load();fail(url); }
            catch(IllegalArgumentException expected) { }
        }
        assertTrue(f.opened.isEmpty());
    }
    @Test public void searchEngineCannotBeUsedAsAnIntermediateHop() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(302,FINAL));f.replies.add(new Reply(302,"https://www.google.com/url?q=synthetic"));
        f.replies.add(new Reply(302,"https://another.example.org/"));fails(f.loader());assertEquals(List.of(ENTRY,FINAL),f.opened);
    }
    @Test public void missingLocationDoesNotExposeAnIntermediatePage() throws Exception {
        for(String location:new String[]{null,""}) {
            Fixture f=new Fixture();f.replies.add(new Reply(302,location));fails(f.loader());assertEquals(List.of(ENTRY),f.opened);
        }
    }
    @Test public void crossHostLoopHasAFiniteUpperLimit() throws Exception {
        Fixture f=new Fixture();
        for(int i=0;i<=NavigationGateway.MAX_REDIRECTS;i++)f.replies.add(new Reply(302,i%2==0?FINAL:ENTRY));
        fails(f.loader());assertEquals(NavigationGateway.MAX_REDIRECTS+1,f.opened.size());
    }
    @Test public void exactRedirectLimitCanStillReturnAPage() throws Exception {
        Fixture f=new Fixture();
        for(int i=0;i<NavigationGateway.MAX_REDIRECTS;i++)f.replies.add(new Reply(302,FINAL+"?step="+i));
        f.replies.add(new Reply(200,null));assertNotNull(f.loader().load());assertEquals(NavigationGateway.MAX_REDIRECTS+1,f.opened.size());
    }
    @Test public void cookieAndSensitiveRequestHeadersAreNotCopiedAcrossHosts() throws Exception {
        Fixture f=new Fixture();Reply a=new Reply(302,FINAL),b=new Reply(200,null);f.replies.add(a);f.replies.add(b);f.loader().load();
        assertEquals("entry=only",a.getRequestProperty("Cookie"));assertEquals("destination=only",b.getRequestProperty("Cookie"));
        for(Reply reply:List.of(a,b)) {
            assertNull(reply.getRequestProperty("Referer"));assertNull(reply.getRequestProperty("Origin"));assertNull(reply.getRequestProperty("Authorization"));
        }
        assertEquals(List.of(ENTRY,FINAL),f.stored);
    }
    @Test public void firstHtmlCspUsesTheFinalOriginAndPreservesAuthorPolicy() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(302,FINAL));f.replies.add(new Reply(200,null));
        NavigationGateway.Page page=f.loader().load();String csp=page.headers.get("content-security-policy");
        assertTrue(csp.startsWith("script-src 'self', "));assertTrue(csp.contains("form-action https://mypage.example.net"));
        assertTrue(csp.contains("frame-src https://mypage.example.net"));assertFalse(csp.contains("entry.example.com"));
    }
    @Test public void preparedOriginIsPinnedForAllLaterGetAndPostRequests() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(302,FINAL));f.replies.add(new Reply(200,null));
        NavigationGateway.Page page=f.loader().load();UrlPolicy pinned=new UrlPolicy(page.url);
        NavigationGateway later=new NavigationGateway(pinned,f.cookies,"test",f.connector);
        assertFalse(pinned.allows(ENTRY));assertTrue(pinned.allows("https://mypage.example.net/next"));
        for(String method:List.of("GET","POST")) {
            int before=f.opened.size();
            try { later.fetch(ENTRY,method,"field=ordinary".getBytes(),"application/x-www-form-urlencoded",FINAL);fail(); }catch(IOException expected) { }
            assertEquals(before,f.opened.size());
            f.replies.add(new Reply(302,"https://outside.example.org/"));before=f.opened.size();
            try { later.fetch(FINAL,method,"field=ordinary".getBytes(),"application/x-www-form-urlencoded",FINAL);fail(); }catch(IOException expected) { }
            assertEquals(before+1,f.opened.size());
        }
        f.replies.add(new Reply(302,"/next"));f.replies.add(new Reply(200,null));
        assertEquals("https://mypage.example.net/next",later.fetch(FINAL,"GET",null,null,null).url);
    }
    @Test public void initialPreparationCannotBeRepeatedAfterSuccess() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(200,null));InitialPageLoader loader=f.loader();loader.load();fails(loader);assertEquals(1,f.opened.size());
    }
    @Test public void failureDoesNotReopenTheInitialAllowance() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(302,"https://bing.com/"));InitialPageLoader loader=f.loader();
        fails(loader);fails(loader);assertEquals(1,f.opened.size());
    }
    @Test public void cancellationBeforeLoadMakesNoRequest() throws Exception {
        Fixture f=new Fixture();InitialPageLoader loader=f.loader();loader.cancel();fails(loader);assertTrue(f.opened.isEmpty());
    }
    @Test public void cancellationDuringResponseStopsCookiesAndTheNextHop() throws Exception {
        Fixture f=new Fixture();InitialPageLoader loader=f.loader();Reply a=new Reply(302,FINAL);a.onResponse=loader::cancel;f.replies.add(a);
        fails(loader);assertEquals(1,f.opened.size());assertTrue(f.stored.isEmpty());
    }
    @Test public void tlsFailurePropagatesWithoutFallbackOrCertificateOverrides() throws Exception {
        Fixture f=new Fixture();
        InitialPageLoader loader=new InitialPageLoader(ENTRY,f.cookies,"test",u->{f.opened.add(u);throw new SSLHandshakeException("synthetic untrusted certificate");});
        try {loader.load();fail();}catch(SSLHandshakeException expected) { }
        assertEquals(List.of(ENTRY),f.opened);assertTrue(f.stored.isEmpty());
    }
    @Test public void nonHtmlFinalResponseIsNotPrepared() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(302,FINAL));Reply b=new Reply(200,null);b.type="application/pdf";f.replies.add(b);fails(f.loader());
        assertEquals(List.of(ENTRY,FINAL),f.opened);
    }
    @Test public void finalCustomPortBecomesThePinnedPort() throws Exception {
        Fixture f=new Fixture();f.replies.add(new Reply(302,"https://mypage.example.net:8443/menu"));f.replies.add(new Reply(200,null));
        UrlPolicy pinned=new UrlPolicy(f.loader().load().url);
        assertTrue(pinned.allows("https://mypage.example.net:8443/next"));assertFalse(pinned.allows(FINAL));
    }
    @Test public void unsupportedThreeHundredResponsesAreNotRenderedAsFinalHtml() throws Exception {
        for(int status:new int[]{300,304,305,306}) {
            Fixture f=new Fixture();f.replies.add(new Reply(status,null));fails(f.loader());assertEquals(List.of(ENTRY),f.opened);
        }
    }
    @Test public void cancellationDuringABodyReadStopsBeforeReturningAPage() throws Exception {
        Fixture f=new Fixture();InitialPageLoader loader=f.loader();
        f.replies.add(new Reply(200,null) {
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream("<html>streaming</html>".getBytes(StandardCharsets.UTF_8)) {
                    @Override public synchronized int read(byte[] buffer,int offset,int length) {
                        loader.cancel();return super.read(buffer,offset,length);
                    }
                };
            }
        });
        fails(loader);assertEquals(List.of(ENTRY),f.opened);
    }
}
