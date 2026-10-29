# テスト報告（2026-10-26）

## 実施済み

- JDK 21、Android SDK Platform 35、Build-Tools 35.0.0、Gradle 8.11.1、AGP 8.9.2で新規ビルド。
- `testDebugUnitTest`: 10件成功。URL形式・検索サイト・同一host/port・転送の判定、GET/POSTと301/302/303/307/308、外部転送前の接続拒否、Cookie・CSP、転送ループの上限を確認。HTTP接続は模擬。
- Playwright / Chromium 154: 10件成功。通常入力・検索文言だけの入力、明示的な検索欄、動的追加・属性変更・Shadow DOM、POSTデータ、SPAの取消し、form.submit、外部リンク・Intent・別窓、外部に紐付く検索欄、同一hostへの移動を確認。デスクトップChromiumでありAndroid WebViewの実機確認ではない。
- `lintDebug`: エラー0。JavaScript有効化についての注意1件。Web注文と検索欄制御に必要な機能として有効にし、origin限定メッセージ・文書取得の検証・CSPを併用。
- `assembleDebug`、`assembleDebugAndroidTest`: 成功。アプリ・統合試験用のAPKを作成。
- `apksigner verify` と `zipalign -c`: 最終APKで検証成功。APK Signature Scheme v2、RSA 2048bitのテスト用debug署名。minSdk 33、targetSdk 35。

## Android統合試験

実際のAndroid WebViewに対する7件の試験を実装・コンパイル済み。HTTPS通信部分のみを模擬し、アプリ本体の検索欄スクリプト、origin限定メッセージ、WebViewClient、POST仲介を使用する。**実行未完了（成功件数には数えない）**。API 35 / Google APIs x86_64 / WebView 124.0.6367.219のエミュレーターで起動を試行したが、KVMが使えずソフトウェア実行となり、Android OS自体がWatchdogで繰り返し再起動した。インストール処理はOS側サービスの未起動・再起動で完了せず、アプリの実行判定に至っていない。OSログとインストール試行ログをverificationに保存した。

監視タイマー延長は自動承認レビューが「システム保護を弱める変更」として拒否したため実行しなかった。先の起動オプションによる延長もエミュレーターが未対応として無視しており、タイマーは変更されていない。WebView・TLS・証明書等の保護を緩めてテストを通す対応は行っていない。

7件の内容は、通常編集と明示的・動的検索欄、同一originリンクと外部移動、通常POSTと同一origin転送、外部POSTと外部転送、WebView直接POSTの拒否、GETフォーム、外部CDNリソースと画面移動の区別。Windowsのハードウェア支援が有効なエミュレーター、またはテスト用実機で `gradlew.bat connectedDebugAndroidTest` を実行できる。

## 未確認

- Windows PCにインストール済みのAndroid Studio・JDK・SDK。読み取り確認スクリプトとセットアップ手順を用意。
- arrows We2でのインストール、起動、カメラ権限・実際のQR、IME・日本語入力、画面サイズ・回転・OSバック操作。
- 実店舗・実際のインターネット接続での注文、TLS、Cookie、CDN、各サイトのボット対策・SPAとの互換性。
- 端末の実際のOSとWebViewバージョン。Android 13以上と必要なWebView機能が条件。

検索欄の完全検出、未知の検索エンジンの分類、改ざん耐性は保証しない。対応範囲・未対応パターンはDESIGN.mdを参照。注文・決済の別ドメイン移動が必要なサービスは今回の仕様では使えない。POST後に遮断された場合、注文自体が成立している可能性があるので店舗に確認し、重複送信を避ける。

## APK識別情報

- ファイル: `dist/restricted-browser-test.apk`
- アプリID: `jp.example.restrictedbrowser.test`
- バージョン: `0.1.0-test-debug` / versionCode 1
- APK SHA-256: `515f70743c24a79fb6c5a0df0f187418055e7ce1e85be7e064115bc7b852ecb7`
- 署名証明書SHA-256: `dbf8a6b77bea4738a334de79db4809a314ef3ce686be50235635367cbe2a31a9`
- 秘密鍵はソースへ含めない。PCで再ビルドすると別のdebug鍵になる場合がある（上書き不可の場合は導入手順を参照）。

`verification/`に単体試験XML、ブラウザ試験ログ、ビルド・lint・署名検証の結果を保存した。Android統合試験はビルドのみ完了し、実行未完了である。
