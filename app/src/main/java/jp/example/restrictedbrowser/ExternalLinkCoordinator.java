package jp.example.restrictedbrowser;

/** Coordinates independent external launches; never changes an existing session's pinned origin. */
final class ExternalLinkCoordinator {
    interface Host {
        boolean hasSession();
        boolean isResetting();
        void dismissConfirmation();
        void prepareForExternalLink();
        void confirmReplacement(String domain, long requestId);
        void resetSession();
        void open(String url);
        void reject(String message);
    }

    private final Host host;
    private String pending;
    private long requestId;
    private boolean awaitingConfirmation;
    private boolean closed;

    ExternalLinkCoordinator(Host host) { this.host = host; }

    void receive(String rawUrl) {
        if (closed) return;
        final String url;
        try {
            // Intents require a complete URL: no prefix completion, search fallback or extra fields.
            url = UrlPolicy.parse(rawUrl).toASCIIString();
        } catch (IllegalArgumentException e) {
            host.reject(e.getMessage());
            return; // Invalid input must not end a session or replace an already pending valid link.
        }
        requestId++;
        host.dismissConfirmation();
        pending = url;
        awaitingConfirmation = host.hasSession();
        host.prepareForExternalLink();
        if (awaitingConfirmation) {
            host.confirmReplacement(new UrlPolicy(url).host, requestId);
        } else {
            drain();
        }
    }

    void confirm(long id) {
        if (closed || !awaitingConfirmation || id != requestId) return;
        awaitingConfirmation = false;
        host.resetSession(); // May complete asynchronously; do not open until cookies are cleared.
        drain();
    }

    void cancel(long id) {
        if (closed || !awaitingConfirmation || id != requestId) return;
        clear();
    }

    void onResetComplete() { drain(); }

    void clear() {
        requestId++;
        awaitingConfirmation = false;
        pending = null;
        host.dismissConfirmation();
    }

    void close() {
        closed = true;
        clear();
    }

    private void drain() {
        if (closed || pending == null || awaitingConfirmation || host.isResetting() || host.hasSession()) return;
        String url = pending;
        pending = null; // Consume once, before callbacks (including a synchronous/reentrant callback).
        host.open(url);
    }
}
