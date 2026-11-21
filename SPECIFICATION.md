# Kurakagi 詳細仕様書

**バージョン**: 1.0.0 (`versionCode: 1`)  
**パッケージ名**: `io.github.drawerdev.kurakagi`  
**対応 OS**: Android 2.1 (API 7) 〜 Android 14 (API 34)  

---

## 1. 概要・設計コンセプト

**Kurakagi** は、ネットワークに接続されていない古い Android 端末（Android 2.1 Eclair 以降）でも安全・高精度かつモダンな UI で運用できる、**完全オフライン対応の二要素認証（TOTP）アプリケーション**です。

### 主要コンセプト
1. **超広範囲な互換性（API 7 〜 API 34）**:
   - Kotlin や AndroidX などの外部フレームワークを一切使用せず、Pure Java と標準 Android SDK のみで構築。
   - `d8 --min-api 7` による単一 DEX (`classes.dex`) 変換と `v1 + v2 + v3` 多重署名により、Android 2.1 の Dalvik VM から Android 10〜14 の最新 ART 環境まで同一の APK（約 330KB）で動作。
2. **完全オフライン運用**:
   - トークンの生成・アカウント管理・QRコードの読み取り／生成のすべてを端末ローカルで完結（外部 API コールや CDN 通信は一切なし）。
   - 通信は一切しない（INTERNET の権限なし）。
3. **時計ズレ（RTC ドリフト）への耐性**:
   - 古いオフライン端末特有の時刻ズレに対処するため、**「過去・現在・未来の3連コード同時表示」**、**「手動秒数オフセット設定」** の2つの対策を搭載。
4. **端末破損・移行への耐性**:
   - 登録済みアカウントを**パスワード保護された暗号化 JSON（AES-128-CBC + PBKDF2-HMAC-SHA1）** または平文 JSON としてエクスポート／インポート可能。
   - 個別アカウント画面でシークレット文字列および標準 `otpauth://` QR コードをいつでも再表示可能。

---

## 2. プロジェクト構造とモジュール構成

### 2.1 Java ソースコード (`app/src/main/java/io/github/drawerdev/kurakagi/`)

| パッケージ | クラス名 | 役割・責務 |
|---|---|---|
| `core` | `TotpCore` | RFC 6238 / RFC 4226 準拠の TOTP / HOTP 算出エンジン（64bit 整数対応、ゼロ埋め保証） |
| `core` | `Base32` | RFC 4648 準拠 Base32 エンコーダー／デコーダー（小文字・スペース・ハイフン自動サニタイズ） |
| `core` | `OtpUriParser` | `otpauth://totp/...` URI の双方向パーサーおよびシリアライザー |
| `core` | `TimeSyncManager` | 端末時刻に対する手動秒数オフセットおよび基準時刻計算の管理 |
| `qr` | `QrEncoder` | ZXing `QRCodeWriter` を用いた ISO/IEC 18004 準拠 QR マトリックス生成エンジン |
| `qr` | `QrMatrix` | QR モジュールの2次元ビット配列保持および Android 用ピクセル配列変換 |
| `qr` | `QrYuvDecoder` | ZXing `QRCodeReader` + `PlanarYUVLuminanceSource` による高速 QR デコーダー |
| `storage` | `Account` | アカウントエンティティ（ID, Label, Issuer, Secret, Algorithm, Digits, Period） |
| `storage` | `TokenStorage` | JSON シリアライズ／デシリアライズおよび AES-128-CBC + PBKDF2 暗号化バックアップ処理 |
| `ui` | `MainActivity` | メイン画面（トークン一覧、全体カウントダウンプログレス） |
| `ui` | `ScanActivity` | QR スキャン画面（縦画面カメラ制御、照準 HUD、オートフォーカス、エラーデバウンス） |
| `ui` | `ManualAddActivity` | 手動登録画面（シークレット・発行元・桁数・アルゴリズム・周期入力） |
| `ui` | `TokenDetailActivity` | トークン詳細画面（シークレット確認／コピー、QR コード再生成表示、削除） |
| `ui` | `SettingsActivity` | 設定画面（手動オフセット設定、暗号化バックアップの書き出し／読み込み、テーマ・言語、一番下に「情報」） |
| `ui` | `TokenAdapter` | トークンカード用リストアダプター（タップコピー、展開式3連コード切替、プログレス更新） |
| `ui` | `TokenStorageHelper` | `SharedPreferences` によるローカル暗号化／JSON データ永続化ヘルパー |
| `ui` | `ViewFinderView` | カスタム照準枠ビュー（シアン角丸コーナー、レーザーライン、半透明マスク描画） |
| `ui` | `PermissionHelper` | リフレクションによる API 23+ ランタイム権限要求と API 7 互換維持ヘルパー |

### 2.2 外部ライブラリ (`app/libs/`)
- `core-2.3.0.jar`: ZXing (Zebra Crossing) 2.3.0 Core ライブラリ（Java 7 互換・純粋 Java QR エンコード／デコードエンジン）

### 2.3 リソース構成 (`app/src/main/res/`)
- **レイアウト (`layout/`)**: `activity_main.xml`, `activity_scan.xml`, `activity_manual_add.xml`, `activity_token_detail.xml`, `activity_settings.xml`, `item_token.xml`
- **ドローアブル (`drawable/`)**: カード背景 (`bg_card.xml`, `bg_card_highlight.xml`)、ボタン背景 (`bg_btn_primary.xml`, `bg_btn_secondary.xml`, `bg_btn_compact.xml`, `bg_btn_accent.xml`, `bg_btn_danger.xml`)、スロット枠 (`bg_slot_box.xml`, `bg_slot_box_active.xml`)、プログレスバー (`progress_bar_custom.xml`)
- **テーマ・カラー (`values/`)**: ダークネイビー基調 (`#0D1117` / `#161B22`) にシアンアクセント (`#00E5FF`) を配したモダン UI 定義 (`colors.xml`, `styles.xml`, `strings.xml`)

---

## 3. コア仕様・暗号／計算エンジン

### 3.1 TOTP 算出 (`TotpCore`)
- **標準規格**: RFC 6238 (TOTP) / RFC 4226 (HOTP)
- **対応アルゴリズム**: `HmacSHA1`（デフォルト）, `HmacSHA256`, `HmacSHA512`
- **対応桁数**: 6桁（デフォルト）, 8桁
- **タイムステップ**: デフォルト 30秒（任意の正の整数秒に対応）
- **64bit タイムスタンプ対応**: カウンタ計算に `long`（64bit 符号付き整数）を使用し、2038年問題以降のタイムスタンプ（例: $T = 20000000000$ 秒）でもオーバーフローせずに正確なトークンを算出。
- **ゼロパディング**: 先頭が `0` で始まるコード（例: `081804`, `005924`）も文字列フォーマットで桁落ちせず厳密に保証。

### 3.2 Base32 デコード耐性 (`Base32`)
- **入力サニタイズ**: 小文字の自動大文字変換、空白・タブ・改行・ハイフン（`-`）の自動除去。
- **パディング処理**: 末尾の `=` パディングの有無や、8文字の倍数に満たない不完全長でも正常にデコード。
- **不正文字防御**: Base32 アルファベット（`A-Z`, `2-7`）以外の文字（`0`, `1`, `8`, `9`, 記号等）が含まれる場合は `IllegalArgumentException` をスローし、UI・パーサー側でクラッシュせず安全にエラー処理。

### 3.3 データ永続化とバックアップ暗号化 (`TokenStorage`)
- **フォーマット**: UTF-8 JSON 配列形式
- **暗号化バックアップ形式**:
  - 鍵導出: Pure Java 実装の **PBKDF2-HMAC-SHA1**（10,000 イテレーション、ランダム 16byte Salt）
  - 暗号化方式: **AES-128-CBC / PKCS5Padding**（ランダム 16byte IV）
  - ヘッダーおよび整合性チェックにより、誤パスワード入力やファイル破損・改ざん時は `GeneralSecurityException` で安全に拒否。

---

## 4. UI / UX 仕様

### 4.1 メイン画面 (`MainActivity` / `TokenAdapter`)
- **ヘッダー**: アプリアイコン、現在の時刻補正状態表示（例: `補正: ±0s (正確)`）、`設定` ボタン。
- **グローバルカウントダウン**: 30秒周期の残り秒数とシアン色のプログレスバーをリアルタイム更新。
- **トークンカード (`item_token.xml`)**:
  - 発行元・アカウント名、6桁または8桁のワンタイムコード（視認性向上のため3桁区切りスペース表示）。
  - カードタップでワンタイムコードをクリップボードへ即座にコピー（API 7〜10 の旧 `android.text.ClipboardManager` と API 11+ の `android.content.ClipboardManager` の両対応）。
  - **`▼ 3連コード` トグルボタン**: 押下するとカード下部に「過去（-30s）」「現在」「未来（+30s）」の3つのトークンを横並び表示し、端末の時計が前後1ステップ分ズレていてもログイン可能。
  - **`詳細/鍵` ボタン**: `TokenDetailActivity` へ遷移。

### 4.2 QR スキャン画面 (`ScanActivity`)
- **画面向きとカメラ正立**:
  - `screenOrientation="portrait"`（縦画面固定）。
  - `setCameraDisplayOrientation()` により背面カメラ映像を 90度 正立表示（API 8+ リフレクション呼出で API 7 互換維持）。
  - `SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS` の明示により Android 2.1〜2.3 端末でも正常描画。
- **3段分離レイアウト (`activity_scan.xml`)**:
  - 上部ヘッダー、中央カメラプレビュー＆照準 HUD（`ViewFinderView`）、下部ボタンドック（`手動入力に切り替え` / `キャンセル`）を独立配置し、照準枠とボタンの干渉を完全に排除。
- **ZXing 多重フォールバックデコード (`DecodeTask` / `QrYuvDecoder`)**:
  1. 縦画面回転済み Y プレーンの照準枠領域（クワイエットゾーン欠け防止のため上下左右 +15% 余白拡張）でデコード試行。
  2. 未検出時は縦画面全体でフォールバックデコード。
  3. 特殊なセンサー向きの端末救済として、回転前の原画像全体でもフォールバックデコード。
  4. 各段階で通常読み取り失敗時は白黒反転（ネガコード）での読み取りも試行。
- **ピント制御（オートフォーカス）**:
  - `FOCUS_MODE_CONTINUOUS_PICTURE` または `FOCUS_MODE_AUTO` を自動選択。
  - **タップフォーカス**: プレビュー画面または照準枠をタップすると即座に `camera.autoFocus()` をトリガー。
  - **定期リフレッシュ**: スキャン中は 2秒間隔でオートフォーカスを再駆動し、近接時のピントボケを防止。
- **不正 QR コード連続読み込み時のエラー制御**:
  - **単一トースト管理 (`showSingleToast`)**: 新規表示前に既存の Toast を `cancel()` してキューへの蓄積を防止。
  - **デバウンス（クールダウン）**: 同一の不正 QR コードに対しては **2.5秒間** エラー再表示とバイブレーションを抑制。
  - **即時消去**: 画面離脱（`onPause` / `onDestroy`）および正常 TOTP QR 検出時にエラー表示を即座に破棄。
  - **成功時のみ振動**: 正しい `otpauth://` QR コードを認識した瞬間のみ 80ms バイブレーションを作動。

---

## 5. Android 互換性・権限設計

- **API 7 互換コーディング規約**:
  - `String.isEmpty()`（API 9+）、`StandardCharsets`（API 19+）、`Objects.requireNonNull`（API 19+）等の非互換 API を一切使用しない。
  - `Camera.setDisplayOrientation`（API 8+）は `Method.invoke` によるリフレクション経由で呼び出し。
- **ランタイム権限 (`PermissionHelper`)**:
  - `targetSdkVersion="34"` 環境において Android 6.0 (API 23) 以降の端末で発生する権限エラーを防ぐため、リフレクションにより `checkSelfPermission` / `requestPermissions` を実装。
  - スキャン起動時に `CAMERA` 権限、設定画面の外部ストレージ入出力時に `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` 権限を動的に要求。
- **マニフェスト互換設定 (`AndroidManifest.xml`)**:
  - `android.hardware.camera` および `autofocus` を `required="false"` とし、カメラ非搭載端末でもインストール・手動運用が可能。
  - `allowBackup="false"` により OS 自動クラウドバックアップからの秘密鍵漏洩を防止。

---

## 6. ビルド・テスト・実機検証結果

### 6.1 ビルド (Gradle)
- Gradle 8.13（Wrapper を同梱）＋ Android Gradle Plugin 8.13.2。`compileSdk 34`・`minSdk 7`・`targetSdk 34`・`versionCode 2`・`versionName 1.0.1`
- Java 8 のソース・バイトコードで書き、D8 が `min-api 7` で 1 つの `classes.dex` にする（コードの縮小・難読化はしない）
- ZXing は Maven Central の `com.google.zxing:core:2.3.0`（1.0.1 まで同梱していた jar と同じ物。SHA-256 で確認）
- リリース版の署名: v1 (JAR) + v2 + v3。鍵とパスワードは `~/.gradle/gradle.properties` の `KURAKAGI_*` から読み、無ければ署名なしの APK を作る
- 単体テスト: `gradlew test` が `HandWrittenTests`（JUnit 4）から `CoreTestRunner` と `ComprehensiveVerificationTest` を走らせる
- **生成物**: `app/build/outputs/apk/release/app-release.apk`（署名後 約 370 KB。2026-11-14 に試しの鍵で 368,733 bytes）

### 6.2 自動テスト結果
- **単体テスト (`CoreTestRunner`)**: **107 / 107 テスト PASS (100%)**
- **包括的検証スイート (`ComprehensiveVerificationTest`)**: **97 / 97 テスト PASS (100%)**
  - RFC 6238 公式テストベクター（6桁 / 8桁、2038年問題超え 64bit、ゼロパディング）
  - Base32 エッジケース（小文字、ハイフン・空白混在、パディング欠如、不正文字防御）
  - 30秒境界値（29.999s → 30.000s）および 3連コード・秒数オフセット検証
  - 多言語 JSON 入出力および AES-128-CBC + PBKDF2 暗号化・改ざん検知検証
- **QR スキャン／生成 E2E テスト (`QrScanUnitTest`)**: **全項目 PASS (100%)**
  - `QrEncoder` 生成 → `QrYuvDecoder.decodeYPlane` 復元、照準枠クロップ復元、90度回転画像復元

### 6.3 実機動作確認済み端末
- **Vodafone VFD 200** (Android 5.1) — インストール、3連コード表示、縦画面 QR スキャン稼働確認
- **Sony Xperia Z5 Compact SO-02H** (Android 7.0) — ランタイム権限要求、カメラプレビュー、UI 表示確認
- **Sony Xperia X Compact SO-02J** (Android 8.0.0) — インストール、起動・UI 表示確認
- **Sony Xperia XZ2 Premium SOV38** (Android 10) — インストール、プロセス起動確認
