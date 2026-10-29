package jp.example.restrictedbrowser;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Exercises asynchronous cleanup and confirmation without real navigation or account data. */
public class ExternalLinkCoordinatorTest {
    static final String A = "https://order.example.com/start";
    static final String B = "https://second.example.net/menu";
    static final class Host implements ExternalLinkCoordinator.Host {
        ExternalLinkCoordinator coordinator;
        boolean session, resetting, synchronousReset;
        int resets, prompts, prepared;
        long promptId;
        String promptDomain;
        List<String> opened = new ArrayList<>(), rejected = new ArrayList<>();
        Host() { coordinator = new ExternalLinkCoordinator(this); }
        public boolean hasSession() { return session; }
        public boolean isResetting() { return resetting; }
        public void dismissConfirmation() { }
        public void prepareForExternalLink() { prepared++; }
        public void confirmReplacement(String domain, long id) { prompts++; promptDomain=domain; promptId=id; }
        public void resetSession() {
            resets++; session=false; resetting=true;
            if (synchronousReset) finishReset();
        }
        public void open(String url) {
            assertFalse("Never open before cleanup completes", resetting);
            assertFalse("Never open a second session", session);
            opened.add(url); session=true;
        }
        public void reject(String message) { rejected.add(message); }
        void finishReset() { resetting=false; coordinator.onResetComplete(); }
    }
    @Test public void coldLaunchWaitsForStartupCookieCleanup() {
        Host h=new Host(); h.resetting=true; h.coordinator.receive(A);
        assertTrue(h.opened.isEmpty()); assertEquals(0,h.prompts);
        h.finishReset(); assertEquals(List.of(A),h.opened);
        h.finishReset(); assertEquals(1,h.opened.size());
    }
    @Test public void idleReadyLaunchOpensImmediately() {
        Host h=new Host(); h.coordinator.receive(A);
        assertEquals(List.of(A),h.opened); assertEquals(0,h.resets);
    }
    @Test public void activeSessionOnlyPromptsBeforeConsent() {
        Host h=new Host();h.session=true;h.coordinator.receive(B);
        assertEquals(1,h.prompts);assertEquals("second.example.net",h.promptDomain);
        assertEquals(0,h.resets);assertTrue(h.session);assertTrue(h.opened.isEmpty());
    }
    @Test public void cancellationKeepsExistingSession() {
        Host h=new Host();h.session=true;h.coordinator.receive(B);
        h.coordinator.cancel(h.promptId);h.finishReset();
        assertTrue(h.session);assertEquals(0,h.resets);assertTrue(h.opened.isEmpty());
    }
    @Test public void consentClosesOldSessionThenWaitsForCookies() {
        Host h=new Host();h.session=true;h.coordinator.receive(B);
        h.coordinator.confirm(h.promptId);
        assertEquals(1,h.resets);assertTrue(h.resetting);assertTrue(h.opened.isEmpty());
        h.finishReset();assertEquals(List.of(B),h.opened);
    }
    @Test public void synchronousCleanupDoesNotOpenTwice() {
        Host h=new Host();h.session=true;h.synchronousReset=true;h.coordinator.receive(B);
        long id=h.promptId;h.coordinator.confirm(id);h.coordinator.confirm(id);
        assertEquals(List.of(B),h.opened);assertEquals(1,h.resets);
    }
    @Test public void invalidAndSearchEngineLinksDoNotDestroyCurrentSession() {
        String[] bad={null,"", "example.com", "検索語", "http://order.example.com", "https://google.co.jp/", "https://www.bing.com/", "intent://x", "javascript:alert(1)", "file:///x", "content://x", "https://user@order.example.com/", "https://order.example.com:0/", "https://order.example.com\\@evil.net/", "https://127.0.0.1/"};
        for (String url:bad) {
            Host h=new Host();h.session=true;h.coordinator.receive(url);
            assertEquals(String.valueOf(url),1,h.rejected.size());assertTrue(h.session);
            assertEquals(0,h.resets);assertEquals(0,h.prompts);assertEquals(0,h.prepared);assertTrue(h.opened.isEmpty());
        }
    }
    @Test public void staleDialogCannotApproveDifferentIncomingLink() {
        Host h=new Host();h.session=true;h.coordinator.receive(A);long old=h.promptId;
        h.coordinator.receive(B);long current=h.promptId;
        h.coordinator.confirm(old);assertEquals(0,h.resets);
        h.coordinator.cancel(old);h.coordinator.confirm(current);h.finishReset();
        assertEquals(List.of(B),h.opened);
    }
    @Test public void latestValidLinkDuringStartupWins() {
        Host h=new Host();h.resetting=true;h.coordinator.receive(A);h.coordinator.receive(B);h.finishReset();
        assertEquals(List.of(B),h.opened);
    }
    @Test public void invalidLinkDoesNotReplaceValidPendingLink() {
        Host h=new Host();h.resetting=true;h.coordinator.receive(A);h.coordinator.receive("https://bing.com/");h.finishReset();
        assertEquals(List.of(A),h.opened);assertEquals(1,h.rejected.size());
    }
    @Test public void latestLinkDuringApprovedCleanupWinsWithoutParallelSession() {
        Host h=new Host();h.session=true;h.coordinator.receive(A);h.coordinator.confirm(h.promptId);
        h.coordinator.receive(B);h.finishReset();
        assertEquals(List.of(B),h.opened);assertEquals(1,h.resets);
    }
    @Test public void destructionDiscardsQueuedLinkAndLateCallbacks() {
        Host h=new Host();h.resetting=true;h.coordinator.receive(A);h.coordinator.close();h.finishReset();h.coordinator.receive(B);
        assertTrue(h.opened.isEmpty());
    }
    @Test public void destructionInvalidatesConfirmation() {
        Host h=new Host();h.session=true;h.coordinator.receive(A);long id=h.promptId;h.coordinator.close();h.coordinator.confirm(id);
        assertEquals(0,h.resets);assertTrue(h.session);
    }
    @Test public void manualHomeRequestDiscardsPendingLink() {
        Host h=new Host();h.resetting=true;h.coordinator.receive(A);h.coordinator.clear();h.finishReset();
        assertTrue(h.opened.isEmpty());
    }
    @Test public void queryAndFragmentSurviveWithoutBeingShownInConfirmation() {
        Host h=new Host();h.session=true;
        String url="https://order.example.com/start?token=synthetic-only#section";h.coordinator.receive(url);
        assertEquals("order.example.com",h.promptDomain);h.coordinator.confirm(h.promptId);h.finishReset();
        assertEquals(List.of(url),h.opened);
    }
    @Test public void sameDomainExternalLinkStillRequiresConfirmation() {
        Host h=new Host();h.session=true;h.coordinator.receive(A);
        assertEquals(1,h.prompts);assertTrue(h.opened.isEmpty());assertEquals(0,h.resets);
    }
    @Test public void lateCancellationAfterApprovalCannotCancelAnAcceptedLink() {
        Host h=new Host();h.session=true;h.coordinator.receive(A);long id=h.promptId;
        h.coordinator.confirm(id);h.coordinator.cancel(id);h.finishReset();
        assertEquals(List.of(A),h.opened);
    }
}
