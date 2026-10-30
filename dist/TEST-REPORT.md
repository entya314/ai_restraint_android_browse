# テスト報告 — 0.3.0-test-debug

## 実施済み

- JDK 21、SDK Platform 35 / Build-Tools 35.0.0、Gradle 8.11.1、AGP 8.9.2で `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 成功。
- **JVM単体テスト48件成功**。初回転送21件、既存のURLポリシー4件・通常GET/POST取得6件・外部リンク調停17件。HTTP接続・Cookie入出力・画面のHostは模擬。
- 初回の301/302/303/307/308をすべてGETで扱い、複数ホスト・相対Locationを解決すること、途中のHTTP・検索エンジン・不正URLを接続前に拒否することを確認。元ホストのCookieヘッダー等を別ホストへコピーせず、URLごとにCookieを取得することも確認。
- 初回HTMLのCSPが最終originに固定され、元のCSPも維持されること、そこから作る通常の取得処理ではGET/POSTの別origin移動・転送が拒否され、同一originの転送は許可されることを確認。
- 初回処理の成功後・失敗後に再試行できないこと、終了前・レスポンス中・本文読取中の中止、最大10回の転送上限、TLS失敗時の中止、非HTML・未対応3xx応答の拒否、最終portへの固定を確認。
- **Playwright / Chromium 154の回帰試験10件成功**。通常入力・明示的/動的検索欄・フォーム仲介・同一host移動・外部移動拒否等。実際のAndroid WebViewではない。
- `lintDebug`: エラー0、JavaScript有効化の注意1件。Web注文・検索欄制御のため有効にし、既存のorigin検証・CSP・TLS設定を維持。
- APK内のコンパイル済みManifestでversionCode 3、標準ブラウザ用のMAIN/APP_BROWSERおよびVIEW/DEFAULT/BROWSABLE、http/https、singleTaskの維持を確認（静的確認）。
- `apksigner verify`、`zipalign -c 4`、ZIP CRC検査成功。RSA 2048bit / APK Signature Scheme v2で、以前の配布版と同じ証明書。APK内guard.jsは現ソースと一致。

## Android統合試験（実行未確認）

**実WebView用12件＋Intent解決4件の計16件をコンパイル済み。Android上の実行は未確認。**

WebView試験の準備を、模擬した入口ホストから最終ホストへのHTTP転送 → 固定したBrowserSession → 取得済みHTMLの表示という経路に変更した。最終ホストへの固定と初回GETの非重複、最初のHTML内のinline JavaScriptによる読み込み終了前の別ホスト移動拒否、元の入口ホスト・meta refreshの拒否、URL正規化時の初回GET非重複、取得済みページの再表示不可を試験に追加した。既存の通常入力・検索欄・GET/POST・外部移動拒否等も含む。

この環境ではKVMがなく、初回試作時のAPI 35エミュレーターでOSのWatchdog再起動が繰り返されたため、今回エミュレーターでの実行は行っていない。保護設定を緩めてテストを通す対応はしていない。初回の起動失敗ログはverificationに保存済み。

## 実機・実サイトで未確認の項目

- arrows We2での更新版インストール、最終ドメイン表示、QR・URL入力・外部リンクからの初回転送。
- 読み込み中の終了・外部リンクの確認切り替え・Activity破棄時に古い完了結果が反映されないことのAndroid実行。
- Androidの標準ブラウザ候補表示・選択、実際の他アプリからの起動、IME・画面回転等。
- 楽天トラベル等の実サイトの現在のHTTP転送形式、実アカウントでのログイン・予約表示。ユーザー情報の入力・注文・予約操作は実施していない。
- 実際のTLS・Cookie属性・CDN・ボット対策等との互換性。TLS失敗は単体試験内で模擬しており、実サーバーとの証明書試験ではない。

表示先は**WebViewにHTMLを渡す前に固定する**。画像読み込み終了まで別originへ移れる仕様ではない。最初のHTML内・表示後のJavaScriptやmeta refresh、ログイン・注文・決済による別ホスト移動は引き続き拒否する。初回に許可するのは新規セッションのHTTP転送のGETだけである。

追加仕様はINITIAL-REDIRECTS.md、手動確認と更新手順はINSTALL-WINDOWS.md。Custom Tabsサービス、検索欄の完全検出、未知の検索サイトの分類、すべてのサイトとの互換性を保証する変更ではない。

## APK識別情報

- ファイル: `dist/restricted-browser-test.apk`
- アプリID: `jp.example.restrictedbrowser.test`
- バージョン: `0.3.0-test-debug` / versionCode 3
- 対応: Android 13（API 33）以上、targetSdk 35
- APK SHA-256: `dd78166531b71ff0b529ddfba61634bc53c7cc1d9405c0e448d3fcafbf6618f1`
- 証明書SHA-256: `dbf8a6b77bea4738a334de79db4809a314ef3ce686be50235635367cbe2a31a9`
- 初回・0.2.0配布APKと同じ証明書。ご自身のPCでビルドしたAPKは異なる鍵の場合があり、上書きできない際の対処は導入手順を参照。

ビルド・単体試験XML・ブラウザ試験・lint・Manifest・署名の記録はverification/。以前の報告はverification/history/に保存。
