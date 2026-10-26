# 設計と制約

## 入力とセッション

入力はURLのみ。scheme省略時はHTTPSを補います。文字列を検索語として送る処理はありません。QRと直接入力は同じUrlPolicyで検証します。QR文字列をAndroid Intentとして実行しません。

HTTPSのみを受け付けます。hostは大文字小文字を正規化して完全一致、portは省略時443として一致が必要です。別サブドメイン、別port、HTTPへの降格、userinfo、不正なauthority、末尾ドット、IPアドレス、単一ラベルhost、逆スラッシュなどは拒否します。国際化ドメインはASCIIのPunycode表記を使用してください。UnicodeのURLパスはエンコードできます。

Google（地域ドメイン群を含む）、Bing、Yahoo、DuckDuckGo、Baidu、Yandex、Brave、Ecosia等をhostのラベル境界で拒否します。`evilgoogle.example`等を部分一致で誤検出しません。リストはUrlPolicy.javaで管理し、設定画面は設けません。世界中の未知の検索サイト、サイト内プロキシ、独自の検索APIを完全に分類するものではありません。

初回のhost・HTTPS・実効portを固定し、転送先を新しい起点として採用しません。`short.example`から`order.example`へ転送する短縮QRも拒否します。画面上は仕様どおりドメイン名のみを表示します。

「終了」やActivity再生成でセッションを閉じ、Cookie・Webストレージ・WebViewキャッシュ・履歴を消去します。回転ではActivityを再生成しません。注文は終了前に店舗へ送信済みの可能性があり、終了操作でサーバー側の注文は取り消せません。WebViewの内部ファイルの完全消去や端末のフォレンジック対策を保証する機能ではありません。

## ナビゲーションとPOST

1. `shouldOverrideUrlLoading`でリンク、JavaScriptによる遷移、フォーム等の非許可URLを拒否します。
2. `shouldInterceptRequest`でメインフレームを再検証します。**WebViewからの直接POSTは許可しません**。この点がshouldOverrideだけの実装との差です。
3. メイン文書のGETと仲介されたPOSTはNavigationGatewayが取得します。HTTPSの通常の証明書・hostname検証を使い、自動転送はOFF。301/302/303/307/308の各LocationをURLとして解決し、毎回同じ固定originと照合してから次へ接続します。別hostへ送信した後で表示だけ止める構造にはしません。
4. 301/302のPOSTと303はGET、307/308はメソッドと本文を維持します。最大10回の転送を許可します。CookieをWebViewと共有し、同じoriginの注文セッションを維持します。RefererとOriginも固定originの検証を経て付けます。
5. 最終文書を1回限りの保留レスポンスとしてWebViewに渡し、最終URLをベースURLにします。HTTP `Content-Security-Policy`ヘッダーにform-action・frame-src・object-src・base-uri制限を追加します。元のCSPは併用します。HTTP Refreshヘッダーは採用しません。meta refreshは通常の遷移検証に従います。

通常フォームは開始時注入スクリプトで取得し、HTMLのsubmitイベントと`HTMLFormElement.prototype.submit()`を仲介します。ボタンのformaction/formmethod/formtarget/formenctype、submitterのname/value、チェック状態、disabled欄、複数値を扱います。GET、URLエンコードPOST、text/plain、テキストフィールドのmultipartをUTF-8で送信します。SPAがpreventDefaultした送信は仲介しません。ページ側がイベントの伝播を止めて仲介されなかったネイティブPOSTは遮断されます。

仲介メッセージはAndroidX WebKitのorigin限定WebMessageListenerを使用します。`addJavascriptInterface`は使いません。ネイティブ側でもURL・メソッド・本文サイズ・送信元・トップフレームを再検証します。メッセージから任意のAndroid機能を実行する機能はありません。

## 外部配信と抜け道

画像・CSS・JavaScript・XHR/fetchなどのリソースは画面遷移と分け、通常のWebViewのHTTPS取得を許可します。外部ドメインのAPIもリソースとして利用できるため、サイト側の通常の非表示通信を全遮断するアプリではありません。混在HTTPコンテンツは拒否します。CSPのframe-srcは固定originのみ、object-srcはnoneです。

新規ウィンドウ、targetが_self以外のリンク・フォーム、Intent・tel・mailto・file・content・data等へのナビゲーション、ダウンロード、ファイル選択、Webページからのカメラ・マイク・位置情報要求は拒否します。テキストの選択操作はコピー・切り取り・貼り付け・全選択を残し、外部検索・共有・PROCESS_TEXT起動を除きます。QRカメラはアプリ内の非exported Activityのみで起動します。

Safe Browsingは有効、証明書エラーはcancel、TLS検証を外すコードやHTTP許可はありません。Service Workerが文書取得を迂回しないようネットワーク利用を遮断し、新規登録も拒否します。必要なWebView機能がない端末では閲覧を開始せず更新を案内します。デバッグ版でもWebViewのリモートデバッグは無効にします。アプリ自体はテスト用debug署名であり、ADBによる開発・アンインストール等を防ぐ機能はありません。

## 検索欄

テキスト、name、id、placeholder、aria-labelなどに「検索」「search」があるだけでは禁止しません。無効化するのは`input[type=search]`、`role=searchbox`、`role=search`、`search`要素と、それらの内部の編集可能コントロールです。MutationObserverで動的追加・属性変化を監視し、入力イベントも捕捉します。新規Shadow Rootも監視します。明示的な検索指定が外れた欄は、アプリが変更したdisabled等を復元します。

**完全検出・改ざん耐性は保証できません。** 未対応の例は、type=textだけで作られた検索、canvasや独自IMEウィジェット、独自JavaScriptで入力を収集するUI、注入前に存在するアクセス不可のclosed Shadow Root、スクリプト自身がAPIを書き換える場合などです。MutationObserverは非同期なので、同じJavaScriptタスク中の一時的な変更や悪意あるプログラムによる値設定をすべて防ぐものではありません。検索の意味を推測して普通の注文欄を巻き込む処理は追加していません。

## 互換性の範囲

- 別ドメイン・別サブドメイン・別portが必要な注文、決済、SSO、外部ブラウザ認証は使えません。
- 同じoriginのiframe表示は可能ですが、iframeからの通常フォーム送信は仲介対象外です。第三者決済iframeはframe-srcと第三者Cookie制限でも拒否されます。
- Service Worker依存のPWA、Basic認証、クライアント証明書、ファイル添付、PDFなどHTML以外の文書、8MiB超のHTML、UTF-8以外を要求するフォーム、特殊なページの送信イベント等は未対応です。
- 仲介により通常ブラウザとHTTPヘッダー・キャッシュ・エラー表示が異なるため、ボット対策等との互換性は保証できません。セキュリティ設定を緩めて通す処理はありません。
- POST後に応答エラーや外部転送の遮断が起きた場合、元の送信先で注文は成立している可能性があります。自動再送せず店舗に確認してください。
- 端末全体の制限、ホームアプリの固定、他アプリの制御、アンインストール防止は実装していません。
