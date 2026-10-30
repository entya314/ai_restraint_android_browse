# Windows PC・arrows We2 導入手順

このチャットからWindows PCやUSB端末には接続していません。PCのツール導入状態、arrows We2のOS・WebView版は未確認です。APKのインストールだけならAndroid Studio・JDKは不要です。ADBで入れる場合はPlatform-Toolsが必要です。

## 1. PCの開発環境を確認

ソースZIPを展開してプロジェクトのフォルダーでPowerShellを開きます。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Check-Environment.ps1
```

このスクリプトはAndroid Studioの標準配置場所、JAVA_HOME/PATHとStudio付属JDK、SDKの環境変数・標準配置場所・local.properties、SDK Platform 35、Build-Tools 35.0.0、ADBと接続端末を読み取り確認します。独自のインストール先にあるStudioは手動で確認してください。環境変数やPC設定は変更しません。

不足があれば、公式 https://developer.android.com/studio からWindows用Android Studioをインストールし、初回セットアップを完了してください。JDKはStudio付属の `jbr` を利用できます。独立したJDKを別途入れる必要はありません。このプロジェクトはJDK 17または21、Gradle 8.11.1、Android Gradle Plugin 8.9.2を想定しています。

Android StudioでこのフォルダーをOpenし、SDK Managerで次を導入してください。

- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0
- Android SDK Platform-Tools
- Android SDK Command-line Tools (latest)（コマンドからSDKを管理する場合）

Settings → Build, Execution, Deployment → Build Tools → Gradle のGradle JDKに付属 `jbr` を選び、Gradle Syncを実行します。SDKライセンスは内容を確認して同意してください。SDKの場所は通常 `%LOCALAPPDATA%\Android\Sdk` です。Android Studioが生成するlocal.propertiesはGit管理しません。

コマンドでビルドする場合は、そのPowerShell内で付属JDKとSDKの場所を指定できます。インストール先が異なる場合は読み替えてください。

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

出力は `app\build\outputs\apk\debug\app-debug.apk`。Gradleがテスト用debug鍵を生成して署名します。添付APKとPCで再ビルドしたAPKは通常署名鍵が異なるため、上書きできない場合は古いテスト版をアンインストールしてから入れ直してください（このアプリ内のデータが消えます）。

## 2. arrows We2を準備

1. 「設定 → デバイス情報」などでAndroidのバージョンを確認します。この版はAndroid 13（API 33）以上が対象です。メニュー名はOS版・通信会社で異なる場合があります。
2. Google PlayでAndroid System WebViewとChromeを更新します。起動前注入・メッセージ機能・Service Worker制御が不足する環境では閲覧を開始しません。制限を外して起動する設定はありません。
3. PCから `restricted-browser-test.apk` を端末のDownloadフォルダーへコピーします。
4. 端末のファイルアプリからAPKを開き、必要な場合に限り、そのファイルアプリの「不明なアプリのインストール」を許可してインストールします。
5. 「制限ブラウザ」を起動します。QR読み取りを初めて使う時だけカメラ権限を許可します。URL入力にカメラ権限は不要です。インストール後、ファイルアプリに付与したインストール許可は戻せます。

テスト用APKはPlayストア配布版ではありません。署名検証とSHA-256値はTEST-REPORT.mdとSHA256SUMS.txtで確認できます。

## 3. ADBでインストールする方法

1. 端末のビルド番号を7回タップして開発者向けオプションを有効にし、「USBデバッグ」をONにします。
2. データ通信可能なUSBケーブルでPCへ接続し、端末に表示されたPCのRSA鍵確認を許可します。
3. APKのあるフォルダーで実行します。

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb install -r .\restricted-browser-test.apk
& $adb shell am start -n jp.example.restrictedbrowser.test/jp.example.restrictedbrowser.MainActivity
```

`unauthorized` は端末側の確認待ち、空欄はケーブル・USB接続モード・Windowsのデバイスマネージャーと端末メーカーの公式USBドライバーを確認してください。複数端末がある場合は `adb -s シリアル番号 ...` を使用します。`scripts\Install-Arrows.ps1` でも同じ手順を実行できます。USBデバッグは確認後にOFFに戻せます。

## 4. 実機で確認する項目

- 起動画面の操作が「QR読み取り」「URL入力」の2つであること。
- カメラ許可・拒否・再試行、照明や印刷品質が異なるQRの読取。
- 自分で管理できる注文テストページで、名前・備考・人数の入力と送信ができること。
- `type=search`、`role=searchbox`、`role=search`内、`search`要素内、後から追加される検索欄へ入力できないこと。
- 「検索」と書かれているだけの普通のtext欄には入力できること。
- 表示開始後は同じhost・同じportの画面移動と送信ができ、別host・サブドメイン・port・検索サイト・外部アプリ・別窓を開けないこと。
- HTTPS画像/CSS/JavaScriptの外部配信が表示できること。証明書エラーやHTTPへの転送は中止されること。
- 「終了」で開始画面に戻り、セッションCookie・Webストレージ・履歴が引き継がれないこと。

実際の注文は店舗の案内に従い、試験中に意図しない注文を送らないでください。送信後に転送が遮断された場合、注文だけ成立している可能性があります。再送する前に店舗へ確認してください。別ドメインでの認証・注文・決済が必要なサービスは、この仕様では利用できません。

## 5. Android統合試験をWindowsで実行する場合

Android StudioのDevice ManagerでGoogle APIs / API 35のエミュレーターを作成し、Windowsの仮想化・ハードウェア支援を有効にして起動を完了してください。アプリのWebViewや証明書設定を緩める必要はありません。テスト対象の端末だけを接続し、プロジェクトのフォルダーで実行します。

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

16件のAndroid統合試験を実装しています（WebViewの試験12件＋Intent解決の試験4件）。WebViewは実際に動かし、文書のHTTPS取得部分は試験内で模擬するため、実店舗へ注文しません。結果は `app\build\reports\androidTests\connected\debug` 以下に出力されます。このクラウド環境ではOSの起動が不安定で、これらのAndroid統合試験の実行は完了していません。

ソース一式のZIPにはGit履歴の `GIT-HISTORY.bundle` も含めます。展開してコードを利用できるほか、Gitを導入済みなら `git clone -b work GIT-HISTORY.bundle restricted-browser` で履歴付きリポジトリを復元できます。

## 6. 他のアプリのリンクを開く／標準ブラウザに設定する（0.2.0以降）

1. 更新版 `restricted-browser-test.apk` をインストールします。今回の配布APKは初回配布版と同じdebug証明書で署名しました。ご自身のPCで再ビルドした版は別の鍵の場合があり、上書きできない場合の対処は前述のとおりです。
2. arrows We2で「設定 → アプリ → デフォルトのアプリ → ブラウザアプリ」を開き、「制限ブラウザ」を選択します。メニュー名はOS版で異なります。アプリから標準設定を自動変更する処理はありません。
3. 別のアプリでHTTPSリンクを押して確認します。ブラウザ選択が出た場合は「制限ブラウザ」を選びます。初回・開始画面では、そのリンクの初回HTTP転送を解決し、最終URLを新しい利用の起点として開きます。
4. 閲覧中に別アプリからリンクを受け取ると確認画面が出ます。「キャンセル」や戻る操作では現在の閲覧を維持し、「終了して開く」で現在のセッションを消去して新しいリンクを開きます。同じドメインのリンクでも確認します。送信済みの注文は取り消されません。
5. HTTP、検索エンジン、不正なURLは拒否します。現在の閲覧をこれらのリンクで終了することはありません。

HTTP/HTTPSを受け付ける宣言は、Androidのブラウザ候補として検出されるために行います。HTTPのページを表示する機能は追加していません。OSによるアプリリンクの振り分けにより、対応アプリが別にあるURLはそちらへ開く場合もあります。

Custom Tabsの専用サービスを必須とするアプリや、特定のブラウザ名を指定するアプリとの互換性は別途必要です。今回の対応は通常の `ACTION_VIEW` によるURL起動です。「共有」操作の `ACTION_SEND` や検索語による起動は対象外です。

ADBから通常の外部リンク起動を手動確認する場合（実機未実施）:

```powershell
& $adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://example.com/' -p jp.example.restrictedbrowser.test
```

一度開始画面から開き、続いて同じコマンドをもう一度実行し、切り替え確認・キャンセル・了承をそれぞれ試してください。さらにHTTPや `https://www.google.com/` では拒否されることを確認してください。実際の店舗への注文URLを試験に使う必要はありません。

## 7. 初回HTTP転送の確認（0.3.0）

更新版は最初のHTMLを取得するまでのHTTP転送を許可し、最終ホストに固定してから表示します。転送前・途中がHTTP、検索エンジン、不正URLなら開きません。画像の読み込み完了まで別ホストへの移動を許可する方式ではありません。

例えば、楽天トラベルの旧URL `https://aps1.travel.rakuten.co.jp/portal/my/prv_page.first?f_tab=1` を入力し、サーバーが `mypage.travel.rakuten.co.jp` へHTTPSのHTTPリダイレクトを返す場合、その転送先を最初のページとして開ける構成です。実サイトの現在の転送形式、アカウントのログイン・予約表示は実機未確認です。HTML表示後に別ホストへのログイン転送が必要なら、そこで止まるのが仕様です。

読み込み中に「終了」を押して開始画面に戻り、待っても古いページが勝手に開かないことも確認してください。読み込み中の外部リンクにも切り替え確認が出ます。QRや他のアプリから受け取ったURLも同じ初回転送処理を通します。TLS設定やWebViewの保護を緩める操作は不要です。
