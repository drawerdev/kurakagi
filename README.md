# Kurakagi（蔵鍵）
### 〜 Android 2.1 (API 7) 下限・完全オフライン対応 2要素認証 (TOTP) クライアント 〜

現代の 2FA アプリ（Aegis, Google Authenticator 等）が切り捨てた **Android 2.1 (Eclair) 〜 5.x 世代のスマートフォンを、オフラインで使う 2 段階認証の専用端末として再生** させるオープンソース認証クライアントです。できること・できないことは「セキュリティの考え方」に書いています。

---

## 🌟 主な特徴と仕様

### 1. 互換性と軽さ
* **対応範囲 (minSdk)**: Android 2.1 Eclair (API 7) 以上
* **動作確認済み**: 下の「動作確認済みの端末」の表を参照。表に無い版は未確認
* **ターゲット環境 (targetSdk)**: Android 14 (API 34)
* **アプリサイズ**: 約 390 KB。Java と Android 標準の部品だけで書き、外部ライブラリは QR の読み書きに使う ZXing 2.3.0 だけ
* **署名**: apksigner で v1 (JAR 署名) + v2 + v3

### 2. 鍵の多重化・バックアップ機能（端末突然死対策）
* **シークレットの確認・コピー**: 登録済みアカウントの Base32 シークレットを表示・コピーできます（紙の控え用）。この画面はスクリーンショットと画面録画を禁止しています。Android 13 以降では、コピーした内容をクリップボードのプレビューに出しません。
* **QR コード再生成機能**: 画面上に `otpauth://` の QR コードを描画。別の端末で読み取って鍵を写せます。
* **暗号化バックアップ**: 全件を 1 つのファイルに書き出します。**必ず**パスワードで暗号化します（平文での書き出しはありません）。Android 4.4 以降はシステムのファイル選択画面で保存先を選び、4.3 以前は SD カードの `Kurakagi` フォルダに保存します。

## 🔒 セキュリティの考え方（できること・できないこと）

| 対象 | 守り方 | 守れないこと |
|---|---|---|
| 端末の中の鍵 | アプリ専用の領域（`MODE_PRIVATE`）に保存。クラウドへの自動バックアップは無効 (`allowBackup="false"`) | **暗号化はしていない。** Android 2.x〜4.2 には鍵を安全に預ける仕組み（Keystore）が無いため。root 化した端末、ロックを解除した端末、端末を手にした攻撃者からは読める |
| バックアップのファイル | 形式 v2: PBKDF2-HMAC-SHA256（既定 60,000 回、回数はファイルに記録）→ AES-256-CBC で暗号化 → HMAC-SHA256 で改ざんを検出（encrypt-then-MAC）。MAC を確かめてから復号する | パスワードが弱ければ総当たりされうる。回数は古い端末で数秒に収まるよう選んだ値で、最新の推奨値より少ない |
| シークレットの画面 | `FLAG_SECURE` でスクリーンショット・録画・最近使ったアプリの縮図を禁止 | 別のカメラで画面を撮られることは防げない |
| 通信 | NTP の時刻合わせ（UDP 123、`pool.ntp.org`）だけ。ボタンを押したときだけ通信する。鍵は送らない | NTP の応答は認証されないので、偽の時刻を返されるとコードがずれる（鍵は漏れない） |

古い端末の OS には、もうセキュリティ更新が来ません。Kurakagi は「ネットにつながない、専用の鍵の端末」として使う前提で作っています。

1.0.0 の暗号化バックアップ（形式 v1）と平文の JSON も読み込めます。平文のファイルを読み込んだときは、削除を促します。

## 📱 動作確認済みの端末

| 端末 | Android | 日付 | 確かめたこと |
|---|---|---|---|
| Vodafone VFD 200 | 5.1 | — | （1.0.1 の確認待ち） |

Android 2.x〜4.x は未確認です。動いた・動かなかった報告を歓迎します。

### 3. 3 重時刻救済システム（時計ズレ耐性完備）
* **3連タイムスロット同時表示 (T-1, T, T+1)**:
  「30秒前」「現在」「30秒後」の 3 つの 6 桁コードを同時に展開・並列表示。時計が 30 秒〜1 分ズレていても確実にログイン成功できます。
* **アプリ内 ±秒数 手動補正オフセット**:
  `[-10s]` `[-1s]` `[+1s]` `[+10s]` のステッパーボタンで秒単位の微調整が可能。
* **ワンタップ SNTP 時刻自動同期**:
  Wi-Fi 接続時に `[NTP]` ボタンをタップするだけで、`pool.ntp.org` (UDP 123) と通信し、ズレを瞬時に自動補正します。

### 4. 2026年水準のモダン・サイバーダーク UI
* Android 2.1 の標準コンポーネントのみを用いながら、カスタム角丸 Shape、サイバーシアン (`#00E5FF`) ハイライト、カウントダウンプログレスバー、アニメーション付き HUD 照準枠などを完全実装。

---

## 📂 プロジェクト構成

```
Kurakagi/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/io/github/drawerdev/kurakagi/
│   │   │   │   ├── core/
│   │   │   │   │   ├── Base32.java         # RFC 4648 Base32 デコード・エンコード・サニタイズ
│   │   │   │   │   ├── OtpUriParser.java   # otpauth:// URI 双方向パーサー
│   │   │   │   │   ├── TimeSyncManager.java# 時刻オフセット＆NTP同期管理
│   │   │   │   │   └── TotpCore.java       # RFC 6238 / RFC 4226 TOTP/HOTP 計算エンジン
│   │   │   │   ├── net/
│   │   │   │   │   └── SntpClient.java     # RFC 4330 SNTP クライアント (UDP 123)
│   │   │   │   ├── qr/
│   │   │   │   │   ├── QrEncoder.java      # 純粋Java製 QRコード生成器 (ISO/IEC 18004)
│   │   │   │   │   ├── QrMatrix.java       # 2D QRマトリックス
│   │   │   │   │   └── QrYuvDecoder.java   # NV21 カメラプレビュー解析＆二値化
│   │   │   │   ├── storage/
│   │   │   │   │   ├── Account.java        # アカウントデータモデル (JSON対応)
│   │   │   │   │   └── TokenStorage.java   # SDカード入出力＆AES/PBKDF2暗号化
│   │   │   │   └── ui/
│   │   │   │       ├── MainActivity.java      # メイン画面 (30sカウントダウン、リスト)
│   │   │   │       ├── ManualAddActivity.java # 手動アカウント追加
│   │   │   │       ├── ScanActivity.java      # 横画面固定 QRカメラリーダー
│   │   │   │       ├── SettingsActivity.java  # 設定・時刻微調整・バックアップ
│   │   │   │       ├── TokenAdapter.java      # 3連コード展開対応モダンアダプター
│   │   │   │       ├── TokenStorageHelper.java# SharedPreferences連携
│   │   │   │       └── ViewFinderView.java    # HUDスキャンライン描画View
│   │   │   └── res/
│   │   │       ├── drawable/       # モダン角丸カード・ボタンスタイル XML
│   │   │       ├── layout/         # 画面レイアウト XML
│   │   │       └── values/         # strings, colors, styles
│   │   └── test/java/io/github/drawerdev/kurakagi/
│   │       └── CoreTestRunner.java # 純粋Java単体テスト実行クラス (91件全PASS)
├── build_apk.ps1                   # 完全自動ビルドスクリプト
└── dist/
    └── Kurakagi-release.apk       # 完成署名済みリリース APK
```

---

## 🛠 ビルド方法

### 単体テストの実行
```powershell
javac -encoding UTF-8 -cp app/libs/core-2.3.0.jar -d build_test @sources_core.txt
java -cp "build_test;app/libs/core-2.3.0.jar" io.github.drawerdev.kurakagi.CoreTestRunner
```
> 107 件（RFC 4226/6238、Base32、バックアップ形式 v2 の改ざん検出、PBKDF2-HMAC-SHA256 の RFC 7914 の値、QR、SNTP など）。**QR の位置検出パターンの 3 件は失敗する**（1.0.0 から。未修正）。

### APK パッケージのビルド
```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```
> 署名鍵とパスワードはソースのフォルダに置きません。鍵は環境変数 `KURAKAGI_KEYSTORE`（無ければ `%USERPROFILE%\keys\kurakagi.keystore`）、パスワードは `KURAKAGI_KS_PASS`（無ければ実行時に入力）。`dist/Kurakagi-release.apk` に v1 + v2 + v3 で署名した APK ができます。

---

## 📜 ライセンス
Apache License 2.0（[LICENSE](LICENSE)）。Copyright 2026 drawerdev。

使っている第三者のもの（詳しくは [NOTICE](NOTICE)）:
* **ZXing Core 2.3.0**（`app/libs/core-2.3.0.jar`）: QR の読み書き。Apache License 2.0
* `app/src/test/java/org/json/`: テストを PC で動かすための最小限の代用品で、アプリには入りません。エラーの文言の一部が JSON-java（Public Domain）と同じです
