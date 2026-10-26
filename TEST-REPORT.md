# テスト報告（2026-10-26）

## 実施済み

- JDK 21、Android SDK Platform 35、Build-Tools 35.0.0、Gradle 8.11.1、AGP 8.9.2で新規ビルド。
- `testDebugUnitTest`: 10件成功。URL形式・検索サイト・同一host/port・転送の判定、GET/POSTと301/302/303/307/308、外部転送前の接続拒否、Cookie・CSP、転送ループの上限を確認。HTTP接続は模擬。
- Playwright / Chromium 154: 10件成功。通常入力・検索文言だけの入力、明示的な検索欄、動的追加・属性変更・Shadow DOM、POSTデータ、SPAの取消し、form.submit、外部リンク・Intent・別窓、外部に紐付く検索欄、同一hostへの移動を確認。デスクトップChromiumでありAndroid WebViewの実機確認ではない。
- `lintDebug`: エラー0。JavaScript有効化についての注意1件。Web注文と検索欄制御に必要な機能として有効にし、origin限定メッセージ・文書取得の検証・CSPを併用。
- `assembleDebug`、`assembleDebugAndroidTest`: 成功。アプリ・統合試験用のAPKを作成。
- `apksigner verify` と `zipalign -c`: 検証済み（最終パッケージの値を追記予定）。

## Android統合試験

実際のAndroid WebViewに対する5件の試験を実装・コンパイル済み。HTTPS通信部分のみを模擬し、アプリ本体の検索欄スクリプト、origin限定メッセージ、WebViewClient、POST仲介を使用する。エミュレーターの起動待ち。結果確定後に更新する。

## 未確認

- Windows PCにインストール済みのAndroid Studio・JDK・SDK。読み取り確認スクリプトとセットアップ手順を用意。
- arrows We2でのインストール、起動、カメラ権限・実際のQR、IME・日本語入力、画面サイズ・回転・OSバック操作。
- 実店舗・実際のインターネット接続での注文、TLS、Cookie、CDN、各サイトのボット対策・SPAとの互換性。
- 端末の実際のOSとWebViewバージョン。Android 13以上と必要なWebView機能が条件。

検索欄の完全検出、未知の検索エンジンの分類、改ざん耐性は保証しない。対応範囲・未対応パターンはDESIGN.mdを参照。注文・決済の別ドメイン移動が必要なサービスは今回の仕様では使えない。POST後に遮断された場合、注文自体が成立している可能性があるので店舗に確認し、重複送信を避ける。
