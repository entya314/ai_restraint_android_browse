package jp.example.restrictedbrowser;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable session origin. All entry paths and every redirect use this parser. */
public final class UrlPolicy {
    private static final Set<String> SEARCH = Set.of(
        "bing.com", "duckduckgo.com", "duck.com", "search.yahoo.com", "search.yahoo.co.jp",
        "yahoo.com", "yahoo.co.jp", "baidu.com", "yandex.com", "yandex.ru", "yandex.kz",
        "ecosia.org", "startpage.com", "brave.com", "search.brave.com", "search.naver.com",
        "naver.com", "search.daum.net", "daum.net", "sogou.com", "so.com", "ask.com",
        "aol.com", "qwant.com", "mojeek.com", "you.com", "perplexity.ai", "kagi.com",
        "google.com", "google.co.jp", "google.co.uk", "google.de", "google.fr",
        "google.cn", "google.com.hk", "google.co.kr", "google.com.au", "google.ca",
        "google.co.in", "google.com.br", "google.it", "google.es", "google.ru",
        "google.co.nz", "google.com.tw", "google.com.sg", "google.co.id", "google.co.th",
        "google.com.vn", "google.com.mx", "google.co.za", "google.ae", "google.ch",
        "google.at", "google.be", "google.nl", "google.pl", "google.se", "google.no",
        "google.dk", "google.fi", "google.ie", "google.pt", "google.gr", "google.cz",
        "google.hu", "google.ro", "google.sk", "google.com.tr", "google.co.il",
        "google.com.ar", "google.cl", "google.com.co", "google.com.pe", "google.com.ph"
    );
    // Google regional domains: fail closed for the google.<cc>, google.co.<cc>, google.com.<cc> families.
    private static final Pattern GOOGLE = Pattern.compile("(^|\\.)google\\.(?:[a-z]{2,3}|(?:co|com)\\.[a-z]{2})$");
    public final String host;
    public final int port;
    public final String origin;

    public UrlPolicy(String first) {
        URI uri = parse(first);
        host = uri.getHost().toLowerCase(Locale.ROOT);
        port = effectivePort(uri);
        origin = "https://" + host + (port == 443 ? "" : ":" + port);
    }
    public static URI parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 8192 || !raw.equals(raw.trim()) ||
                raw.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)) ||
                raw.indexOf('\\') >= 0) throw new IllegalArgumentException("URLの形式を確認してください。");
        try {
            URI u = new URI(raw);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getRawUserInfo() != null ||
                    u.getHost() == null || u.getHost().isEmpty() || u.getHost().endsWith(".") ||
                    u.getRawAuthority().contains("%") || u.getPort() == 0 || u.getPort() > 65535 ||
                    u.getPort() < -1) throw new IllegalArgumentException();
            String host = u.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("[") || !host.contains(".") || host.matches("[0-9.]+") ||
                    !IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).equals(host) || isSearch(host))
                throw new IllegalArgumentException();
            // Disallow malformed empty/non-numeric ports, which URI parsers may treat differently.
            String expected = host + (u.getPort() == -1 ? "" : ":" + u.getPort());
            if (!expected.equalsIgnoreCase(u.getRawAuthority())) throw new IllegalArgumentException();
            return u;
        } catch (Exception e) {
            throw new IllegalArgumentException("HTTPSのURLを入力してください。検索サイト・IPアドレス・不正なURLは利用できません。");
        }
    }
    public static String fromInput(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.contains("://")) value = "https://" + value;
        URI u = parse(value);
        return u.toASCIIString();
    }
    public boolean allows(String raw) {
        try {
            URI u = parse(raw);
            return host.equalsIgnoreCase(u.getHost()) && port == effectivePort(u);
        } catch (IllegalArgumentException e) { return false; }
    }
    public String resolveRedirect(String from, String location) {
        if (location == null || location.isEmpty()) throw new IllegalArgumentException("転送先がありません。");
        String resolved = URI.create(from).resolve(location).toASCIIString();
        if (!allows(resolved)) throw new IllegalArgumentException("別ドメインへの転送を禁止しました。");
        return resolved;
    }
    public static boolean isSearch(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (GOOGLE.matcher(h).find()) return true;
        for (String domain : SEARCH) if (h.equals(domain) || h.endsWith("." + domain)) return true;
        return false;
    }
    private static int effectivePort(URI u) { return u.getPort() == -1 ? 443 : u.getPort(); }
}
