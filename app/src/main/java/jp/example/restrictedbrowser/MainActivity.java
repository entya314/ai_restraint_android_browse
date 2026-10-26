package jp.example.restrictedbrowser;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.InputType;
import android.webkit.CookieManager;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public final class MainActivity extends Activity {
    private BrowserSession session;
    private LinearLayout screen;
    private boolean resetting;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        // Never restore WebView history or a previous ordering session after process recreation.
        resetSession();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(18); b.setAllCaps(false); b.setMinHeight(dp(58)); return b;
    }
    private void home() {
        screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER); screen.setPadding(dp(28), dp(24), dp(28), dp(24));
        screen.setBackgroundColor(Color.rgb(246,248,245));
        Button qr = button("QR読み取り"), url = button("URL入力");
        screen.addView(qr, new LinearLayout.LayoutParams(-1, dp(64)));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(-1, dp(64)); second.topMargin = dp(20);
        screen.addView(url, second); setContentView(screen);
        qr.setOnClickListener(v -> {
            if (!ready()) return;
            new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("利用するサイトのQRコードを読み取ってください")
                .setBeepEnabled(false).setBarcodeImageEnabled(false).setOrientationLocked(false).initiateScan();
        });
        url.setOnClickListener(v -> enterUrl());
    }
    private boolean ready() {
        if (resetting) return false;
        if (!BrowserSession.supported()) {
            new AlertDialog.Builder(this).setMessage("安全な制限に必要な機能がありません。Google Playで Android System WebView と Chrome を更新してから、アプリを再起動してください。")
                .setPositiveButton("閉じる", null).show(); return false;
        }
        return true;
    }
    private void enterUrl() {
        if (!ready()) return;
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setHint("https://example.com/order");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout layout = new LinearLayout(this); layout.setPadding(dp(24), dp(8), dp(24), 0);
        layout.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("URL入力").setView(layout)
            .setPositiveButton("開く", null).setNegativeButton("キャンセル", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String url = UrlPolicy.fromInput(input.getText().toString());
                dialog.dismiss(); open(url);
            } catch (IllegalArgumentException e) { input.setError(e.getMessage()); }
        }));
        dialog.show();
    }
    private void open(String url) {
        if (!ready()) return;
        try {
            // Parsing is shared by QR and typed entry. QR data is never interpreted as an Intent.
            UrlPolicy.parse(url);
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(screen.getWindowToken(), 0);
            session = new BrowserSession(this, url);
            LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
            LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(16), 0, dp(8), 0); bar.setBackgroundColor(Color.rgb(246,248,245));
            TextView host = new TextView(this); host.setText(session.domain()); host.setTextSize(16);
            host.setSingleLine(true); host.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            host.setContentDescription("利用中のドメイン " + session.domain());
            bar.addView(host, new LinearLayout.LayoutParams(0, dp(56), 1)); host.setGravity(Gravity.CENTER_VERTICAL);
            Button exit = button("終了"); exit.setTextSize(16);
            bar.addView(exit, new LinearLayout.LayoutParams(dp(84), dp(56)));
            root.addView(bar, new LinearLayout.LayoutParams(-1, dp(60)));
            root.addView(session.web, new LinearLayout.LayoutParams(-1, 0, 1));
            setContentView(root); exit.setOnClickListener(v -> resetSession()); session.open(url);
        } catch (Exception e) {
            if (session != null) { session.close(); session = null; }
            home(); Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void resetSession() {
        if (session != null) { session.close(); session = null; }
        resetting = true; home();
        CookieManager.getInstance().removeAllCookies(done -> { resetting = false; });
        android.webkit.WebStorage.getInstance().deleteAllData();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        IntentResult scan = IntentIntegrator.parseActivityResult(request, result, data);
        if (scan != null) {
            if (scan.getContents() != null) {
                try { open(UrlPolicy.fromInput(scan.getContents())); }
                catch (IllegalArgumentException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
            }
            return;
        }
        super.onActivityResult(request, result, data);
    }
    @Override public void onBackPressed() {
        if (session != null) {
            if (session.web.canGoBack()) session.web.goBack(); else resetSession();
        } else super.onBackPressed();
    }
    @Override protected void onPause() { if (session != null) session.web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (session != null) session.web.onResume(); }
    @Override protected void onDestroy() {
        if (session != null) { session.close(); session = null; }
        super.onDestroy();
    }
}
