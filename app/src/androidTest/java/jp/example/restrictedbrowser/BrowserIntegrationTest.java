package jp.example.restrictedbrowser;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Real Android WebView, production guard/bridge/interceptor, deterministic simulated HTTPS transport. */
@RunWith(AndroidJUnit4.class)
public class BrowserIntegrationTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    BrowserSession session;
    final List<String> requests = new CopyOnWriteArrayList<>();
    final List<String> postedBodies = new CopyOnWriteArrayList<>();
    void ui(Runnable r) { InstrumentationRegistry.getInstrumentation().runOnMainSync(r); }
    String eval(String js) throws Exception {
        CompletableFuture<String> f=new CompletableFuture<>();
        ui(()->session.web.evaluateJavascript(js,f::complete));
        return f.get(30,TimeUnit.SECONDS);
    }
    void until(String expression) throws Exception {
        long deadline=System.currentTimeMillis()+45000;
        while(System.currentTimeMillis()<deadline) {
            if("true".equals(eval(expression))) return;
            Thread.sleep(200);
        }
        fail("Timed out: "+expression);
    }
    @Before public void setup() throws Exception {
        ui(()->{
            assertTrue("Update Android System WebView",BrowserSession.supported());
            try {
                session=new BrowserSession(rule.getActivity(),"https://shop.example.com/start",url->new FakeConnection(url));
                rule.getActivity().setContentView(session.web); session.open("https://shop.example.com/start");
            } catch(IOException e) { throw new RuntimeException(e); }
        });
        until("!!document.getElementById('name')");
    }
    @After public void teardown() { if(session!=null) ui(()->session.close()); }
    class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream sent=new ByteArrayOutputStream();
        FakeConnection(String u) throws IOException { super(new URL(u)); requests.add(u); }
        public void connect() {} public void disconnect() {} public boolean usingProxy() { return false; }
        @Override public int getResponseCode() {
            if(getRequestMethod().equals("POST")) postedBodies.add(sent.toString(StandardCharsets.UTF_8));
            return url.getPath().equals("/redirect-out") || url.getPath().equals("/post") ? 303 : 200;
        }
        @Override public String getHeaderField(String name) {
            if(name.equalsIgnoreCase("Location")) return url.getPath().equals("/redirect-out") ? "https://evil.example.net/blocked" : "/thanks";
            return null;
        }
        @Override public Map<String,List<String>> getHeaderFields() { return Map.of("Content-Type",List.of("text/html; charset=UTF-8")); }
        @Override public OutputStream getOutputStream() { return sent; }
        @Override public InputStream getInputStream() {
            String html="<!doctype html><html><head><meta charset=utf-8></head><body>";
            if(url.getPath().equals("/start")) html+="<form id=f action='/post' method=post><input id=name name=name><textarea id=note name=note></textarea><input id=people name=people type=number><input id=normal placeholder='検索' name=search><input id=s type=search name=q><div role=search><input id=r></div><input id=box role=searchbox><search><input id=tag></search><button id=send name=commit value=yes>Order</button></form><a id=same href='/next'>next</a><a id=out href='https://evil.example.net/'>external</a>";
            else html+="<p id=done>"+url.getPath()+"</p>";
            return new ByteArrayInputStream((html+"</body></html>").getBytes(StandardCharsets.UTF_8));
        }
    }
    @Test public void normalEditingAndSemanticDynamicSearch() throws Exception {
        assertEquals("true",eval("!name.disabled && !document.getElementById('note').disabled && !people.disabled && !normal.disabled"));
        eval("document.getElementById('name').focus(); document.execCommand('insertText',false,'Alice')");
        until("document.getElementById('name').value==='Alice'");
        until("s.disabled && r.disabled && box.disabled && tag.disabled");
        eval("var e=document.createElement('input');e.type='search';e.id='dynamic';document.body.append(e)");
        until("document.getElementById('dynamic').disabled");
    }
    @Test public void sameOriginLinkAndExternalNavigations() throws Exception {
        eval("document.getElementById('out').click()");
        eval("window.location.href='https://evil.example.net/location'");
        Thread.sleep(500);
        assertTrue(requests.stream().allMatch(u->u.startsWith("https://shop.example.com/")));
        assertEquals("true",eval("!!document.getElementById('name')"));
        assertEquals("null",eval("window.open('https://evil.example.net/popup')"));
        eval("document.getElementById('same').click()"); until("!!document.getElementById('done')");
        assertTrue(requests.contains("https://shop.example.com/next"));
    }
    @Test public void ordinaryPostBodyAndSameOriginRedirect() throws Exception {
        eval("document.getElementById('name').value='Alice';note.value='no onion';people.value='2';document.getElementById('send').click()");
        until("!!document.getElementById('done') && document.getElementById('done').textContent==='/thanks'");
        assertEquals(1,postedBodies.size());
        assertTrue(postedBodies.get(0).contains("name=Alice")); assertTrue(postedBodies.get(0).contains("people=2"));
        assertTrue(postedBodies.get(0).contains("commit=yes")); assertFalse(postedBodies.get(0).contains("q="));
    }
    @Test public void crossOriginPostAndPostRedirectNeverReachExternalHost() throws Exception {
        eval("document.getElementById('f').action='https://evil.example.net/order';document.getElementById('send').click()");
        Thread.sleep(500); assertTrue(postedBodies.isEmpty());
        eval("document.getElementById('f').action='/redirect-out';document.getElementById('send').click()");
        long deadline=System.currentTimeMillis()+30000;
        while(postedBodies.isEmpty() && System.currentTimeMillis()<deadline) Thread.sleep(100);
        assertEquals(1,postedBodies.size());
        assertFalse(requests.stream().anyMatch(u->u.contains("evil.example.net")));
    }
    @Test public void rawWebViewPostFailsClosedBeforeTransport() throws Exception {
        int before=requests.size();
        ui(()->session.web.postUrl("https://shop.example.com/native-post","name=Alice".getBytes(StandardCharsets.UTF_8)));
        until("document.body && document.body.textContent.includes('表示できません')");
        assertEquals(before,requests.size()); assertTrue(postedBodies.isEmpty());
    }
}
