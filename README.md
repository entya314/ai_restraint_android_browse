# 制限ブラウザ / Restricted Browser

arrows We2向けの署名済みテスト用Androidアプリ。現行版は `0.2.0-test-debug`（versionCode 2）。初期画面は「QR読み取り」「URL入力」、閲覧中のアプリ側UIはドメイン名と「終了」だけです。検索エンジンへ転送する機能、タブ、設定、履歴一覧、推薦はありません。

- 依頼原文: [SPEC.md](SPEC.md)
- Windows環境確認・APK導入: [INSTALL-WINDOWS.md](INSTALL-WINDOWS.md)
- 実施済み試験と未確認事項: [TEST-REPORT.md](TEST-REPORT.md)
- 外部リンク対応の追加仕様: [EXTERNAL-LINKS.md](EXTERNAL-LINKS.md)
- 設計と対応範囲: [DESIGN.md](DESIGN.md)

他のアプリの通常のHTTP/HTTPSリンクを受け取り、Androidの標準ブラウザ候補に登録します。閲覧中は確認してからセッションを終了し、新しいリンクを開きます。更新後はAndroidの「デフォルトのアプリ → ブラウザアプリ」で選択してください。HTTPは受信後に拒否し、HTTPS限定の制限を維持します。Custom Tabsのサービスは提供しません。

Android 13以上。Android SDK 35 / Build-Tools 35.0.0 / JDK 17または21。Android Studioは必須ではありません。JDK、Android SDKがあればGradle Wrapperからビルドできます。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

一式ZIP: `dist/restricted-browser-complete.zip`（ソース・APK・手順・試験記録・Git履歴）。

保存済みAPK: `dist/restricted-browser-test.apk`。ビルド出力: `app/build/outputs/apk/debug/app-debug.apk`（debug鍵で署名）。アプリID: `jp.example.restrictedbrowser.test`。

JavaScriptのブラウザ試験:

```sh
cd tests
npm ci
CHROMIUM_PATH=/path/to/chromium npm test
```

テスト時だけPlaywrightを使用します。APKには含まれません。実際のWebViewを使うAndroid統合試験（通信はテスト内で模擬）:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

開発中の端末・エミュレーターのみを接続して実行してください。実際の店舗への注文はテストに含めません。
