# テスト報告 — 0.2.0-test-debug

## 実施済み

- JDK 21、SDK Platform 35 / Build-Tools 35.0.0、Gradle 8.11.1、AGP 8.9.2で `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 成功。
- **JVM単体テスト27件成功**。既存のURLポリシー4件・GET/POST転送取得6件に、外部リンク調停17件を追加。現在の閲覧がある場合の了承待ち、キャンセル、Cookie消去の非同期・同期完了、古い確認結果の無効化、複数リンク、無効・検索エンジンURLの拒否、破棄後コールバック、同じドメインでも確認することを検証。Cookie消去・画面表示はテストのHostで模擬しておりAndroid実行試験とは区別する。
- **Playwright / Chromium 154の回帰試験10件成功**。通常入力、明示的・動的検索欄、フォーム仲介、外部移動拒否、同一host移動など。実際のAndroid WebViewや実サイトの試験ではない。
- `lintDebug`: エラー0、JavaScript有効化の注意1件。Web注文・検索欄制御のため有効にし、従来のorigin検証・文書取得の制御・CSPを維持。
- APKのコンパイル済みManifestをaaptで確認。MAIN/LAUNCHERと分離したMAIN/DEFAULT/APP_BROWSERとVIEW/DEFAULT/BROWSABLE、host制限なしのhttp/https、exported、singleTaskを確認。**Android端末の実際のリンク解決・標準ブラウザ候補表示は未確認**。
- `apksigner verify` 成功。RSA 2048bit / APK Signature Scheme v2。前回配布版と同じ署名証明書。
- `zipalign -c 4`、ZIP CRC検査成功。配布APK内のguard.jsとソースの一致を確認。

## Android統合試験（実行未確認）

既存の実WebView試験7件と、今回追加したIntent解決試験4件の**計11件をコンパイル済み**。今回の4件は任意hostのHTTPS、HTTPがBROWSABLEとして解決されること、fileの受信対象外、およびAPP_BROWSERセレクターで起動できることを確認する試験。

この環境にはKVMがなく、初回試作時にAPI 35エミュレーターでOSがWatchdogにより再起動を繰り返したため、今回これらのAndroid実行試験は行っていない。保護設定を変更してテストを通す対応はしていない。初回のOSログはverificationに保存済み。

## arrows We2等で未確認の項目

- 更新版APKのインストール・標準ブラウザ設定画面への候補表示・選択。
- 実際の他アプリからの新規起動・起動済みActivityへの受信、確認画面のキャンセル／了承、OSによるアプリリンクの振り分け。
- QR読み取り中やURL入力中の外部起動、OSによるプロセス終了・再生成、IME・画面回転など。
- 実サイトでのTLS・Cookie・CDN・注文・認証。検索欄の完全検出や全サイトとの互換性を保証するものではない。
- Custom Tabs専用サービスを必須とするアプリへの対応は今回の範囲外。通常のACTION_VIEWを受け取る変更。

手動確認手順はINSTALL-WINDOWS.mdの第6節、追加仕様はEXTERNAL-LINKS.mdを参照。HTTPは受信できる宣言をするが、表示前に拒否する。閲覧中の外部リンクは新たな利用を開始する操作であり、WebView内の別origin移動の制限を緩めるものではない。

## APK識別情報

- ファイル: `dist/restricted-browser-test.apk`
- アプリID: `jp.example.restrictedbrowser.test`
- バージョン: `0.2.0-test-debug` / versionCode 2
- 対応: Android 13（API 33）以上、targetSdk 35
- APK SHA-256: `50618968e96aed865c0512daf391ccfbfee63b77f6ca8d49ca27b9892ff452da`
- 証明書SHA-256: `dbf8a6b77bea4738a334de79db4809a314ef3ce686be50235635367cbe2a31a9`
- 旧配布APKと同じ証明書。PCでご自身がビルドしたAPKは異なる鍵の場合があり、上書きできない際の手順は導入文書を参照。

ビルド・単体試験XML・ブラウザ試験・lint・Manifest・署名の記録はverification/。初回版の報告はverification/history/TEST-REPORT-v0.1.mdに保存。
