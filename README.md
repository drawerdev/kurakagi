# Kurakagi

Apps for old Android phones.

Kurakagi is an offline two-factor authentication (TOTP) app that runs from Android 2.1 up.
It turns an old phone into a dedicated, offline device for your one-time codes.
It is written in plain Java with the standard Android View toolkit, so it runs on very old devices.

- No ads, no tracking. No network use at all (no INTERNET permission). Your keys are never sent.
- Shows three codes at once (previous, current, next 30 seconds), so a phone whose clock is a little off still works. If the clock is off, set the offset by hand in Settings (±1 s / ±10 s).
- Add accounts by scanning a QR code or by typing the secret. Show an account's secret or its QR code again to copy it to another device.
- Encrypted backup of all accounts to one file (always password-protected).
- English and Japanese.

## Download

Get it from <https://drawerdev.github.io/> or this repository's Releases.
Each release lists the APK's SHA-256 and the signing certificate's SHA-256 fingerprint.
Copies from anywhere else may be fake.

On Android 2.x–4.x, download the APK on a PC or another phone, then copy it to the device.

## Designed for

| | |
|---|---|
| minSdk | 7 (Android 2.1) |
| targetSdk | 34 (Android 14) |

## Permissions

| Permission | Why |
|---|---|
| `android.permission.CAMERA` | Scanning `otpauth://` QR codes to add an account. The camera is optional; you can add accounts by hand. |
| `android.permission.VIBRATE` | A short vibration when a QR code is read. |
| `android.permission.READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | Android 4.3 and older only (`maxSdkVersion` 18): reading and writing encrypted backups in the `Kurakagi` folder on the SD card. Android 4.4 and later use the system file picker and do not ask for this. |

## Security: what Kurakagi protects and what it cannot

| What | How | What it cannot protect against |
|---|---|---|
| Keys on the phone | Stored in the app's private storage (`MODE_PRIVATE`). Cloud auto-backup is off (`allowBackup="false"`). | **The keys are not encrypted.** Android 2.x–4.2 has no safe place to keep a key (Keystore). A rooted phone, an unlocked phone, or someone holding the phone can read them. |
| Backup file | Format v2: PBKDF2-HMAC-SHA256 (60,000 iterations by default; the count is stored in the file) → AES-256-CBC → HMAC-SHA256 to detect tampering (encrypt-then-MAC). The MAC is checked before decrypting. | A weak password can be guessed. The iteration count is chosen to take a few seconds on old phones, which is lower than current recommendations. |
| Screens that show a secret | `FLAG_SECURE` blocks screenshots, screen recording and the recent-apps thumbnail. | Someone photographing the screen with another camera. |
| Network | None. The app has no INTERNET permission. | If the phone's clock drifts, the codes drift too; fix it with the manual offset in Settings. |

Old phones no longer get security updates. Kurakagi is meant to be used as a dedicated key device that stays off the network.

## Backups

- Backups are always encrypted with your password (at least 8 characters). There is no plain-text export.
- Android 4.4 and later: you choose where to save with the system file picker. Android 4.3 and older: the file goes to the `Kurakagi` folder on the SD card.
- Kurakagi can still read 1.0.0 backups (encrypted format v1) and plain JSON from early test versions. After reading a plain file, it asks you to delete it.
- Backups made with a different PBKDF2 iteration count can still be read, because the count is stored in the file.

## Tested by the author

| Device | Android | How | What was checked | Date |
|---|---|---|---|---|
| Sony Xperia acro HD (SO-03D) | 4.0.4 | real phone | Everything: adding by QR and by hand, codes, showing the QR and the secret, encrypted backup and restore. Encrypting takes about 3–4 seconds. | 2026-11 |
| Vodafone VFD 200 | 5.1 | real phone | Scanning a QR code, the About screen | 2026-11 |
| Sony Xperia Z5 Compact (SO-02H) | 7.0 | real phone | Launch, scanning a QR code | 2026-11 |
| Sony Xperia X Compact (SO-02J) | 8.0 | real phone | Launch | 2026-11 |
| Emulator | 2.3.7 | emulator | Launch, adding by hand, codes, showing the QR, encrypted backup to the SD card and restore, a wrong password is refused, the scan screen opens | 2026-11 |

Not checked on a real phone yet: anything on Android 2.x, including how long encryption takes and scanning QR codes with the camera. Android 3.x has not been checked at all.

## Reports from users

| Device | Android | Result | Report | Date |
|---|---|---|---|---|
| — | — | — | — | — |

Does it work on your phone? Tell us with the "Device report" issue form, or the form at <https://drawerdev.github.io/>.

## Build

Requirements: JDK 21 and the Android SDK (set `sdk.dir` in `local.properties`
or the `ANDROID_HOME` environment variable).

```sh
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
The debug build installs as `io.github.drawerdev.kurakagi.debug`, next to the release build, so it never replaces your real accounts.

`gradlew test` runs `CoreTestRunner` (RFC 4226/6238, Base32, backup format v2 including tamper detection, PBKDF2-HMAC-SHA256, QR and more) and `ComprehensiveVerificationTest`.
`gradlew lint` fails when code uses an API newer than Android 2.1 without a version check (`NewApi`).

Release signing: the keystore and its passwords are not in this repository.
Put these four values in `~/.gradle/gradle.properties` (or environment variables with the same names):

```properties
KURAKAGI_KEYSTORE=/path/to/kurakagi.keystore
KURAKAGI_KEYSTORE_PASSWORD=...
KURAKAGI_KEY_ALIAS=...
KURAKAGI_KEY_PASSWORD=...
```

Without them, `./gradlew assembleRelease` writes an unsigned `app-release-unsigned.apk`. Signed releases use v1 (JAR), v2 and v3 signatures.

## Report a problem

Press "Copy error report" in Settings → About (it copies only the app version, the Android version and the device model), then open an issue at
<https://github.com/drawerdev/kurakagi/issues>.
Never paste a secret, a QR code or an account name into an issue.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Kurakagi uses ZXing core 2.3.0 (Apache-2.0) for reading and drawing QR codes.

## Other apps by drawerdev

- Mataoto — a music player and home screen (Android 5.0 and later)
- Mataoto Lite — a lighter version of Mataoto (Android 2.1 and later)
- HauntChat — an AI chat app that works on old phones too (Android 2.3 and later)

See <https://drawerdev.github.io/> for the full list.

## Support

If you like the app, you can support drawerdev on OFUSE or GitHub Sponsors. The links are on <https://drawerdev.github.io/>.

---

# Kurakagi（蔵鍵、日本語）

古い Android のためのアプリを作っています。

Kurakagi は、Android 2.1 から動く、オフラインの 2 段階認証（TOTP）アプリです。
古いスマホを、ワンタイムのコード専用の、ネットにつながない端末として使えるようにします。
Java と Android の標準の部品（View）だけで書いているので、とても古い端末でも動きます。

- 広告・追跡なし。通信しません（INTERNET の権限なし）。鍵は送りません。
- 3 つのコード（30 秒前・今・30 秒後）を同時に出すので、時計が少しずれた端末でも使えます。時刻がずれたら、設定の手の補正で直します（±1 秒・±10 秒）。
- QR コードの読み取りか、シークレットの手入力でアカウントを足せます。登録したアカウントのシークレットや QR コードを出して、別の端末へ写せます。
- 全部のアカウントを 1 つのファイルに暗号化して書き出せます（必ずパスワードで守ります）。
- 英語と日本語。

## 入手

<https://drawerdev.github.io/> か、このリポジトリの Releases から入れてください。
各リリースに、APK の SHA-256 と、署名の証明書の指紋（SHA-256）を載せています。
ほかの場所から入れたものは、偽物の可能性があります。

Android 2.x〜4.x では、PC や別のスマホで APK を保存してから、端末へ送ってください。

## 設計の対象

| | |
|---|---|
| minSdk | 7（Android 2.1） |
| targetSdk | 34（Android 14） |

## 権限

| 権限 | 使う理由 |
|---|---|
| `android.permission.CAMERA` | `otpauth://` の QR コードを読んでアカウントを足すため。カメラが無くても、手入力で足せます。 |
| `android.permission.VIBRATE` | QR コードを読めたときに、短く震わせるため。 |
| `android.permission.READ_EXTERNAL_STORAGE`・`WRITE_EXTERNAL_STORAGE` | Android 4.3 以前だけ（`maxSdkVersion` 18）。SD カードの `Kurakagi` フォルダに、暗号化したバックアップを読み書きするため。4.4 以降はシステムのファイル選択画面を使うので、この権限は求めません。 |

## セキュリティの考え方（できること・できないこと）

| 対象 | 守り方 | 守れないこと |
|---|---|---|
| 端末の中の鍵 | アプリ専用の領域（`MODE_PRIVATE`）に保存。クラウドへの自動バックアップは無効（`allowBackup="false"`） | **暗号化はしていない。** Android 2.x〜4.2 には鍵を安全に預ける仕組み（Keystore）が無いため。root 化した端末、ロックを解除した端末、端末を手にした攻撃者からは読める |
| バックアップのファイル | 形式 v2: PBKDF2-HMAC-SHA256（既定 60,000 回、回数はファイルに記録）→ AES-256-CBC で暗号化 → HMAC-SHA256 で改ざんを検出（encrypt-then-MAC）。MAC を確かめてから復号する | パスワードが弱ければ総当たりされうる。回数は古い端末で数秒に収まるよう選んだ値で、最新の推奨値より少ない |
| シークレットの画面 | `FLAG_SECURE` でスクリーンショット・録画・最近使ったアプリの縮図を禁止 | 別のカメラで画面を撮られることは防げない |
| 通信 | 通信しない。INTERNET の権限なし | 端末の時計がずれるとコードもずれる。設定の手の補正で直す |

古い端末の OS には、もうセキュリティ更新が来ません。Kurakagi は「ネットにつながない、専用の鍵の端末」として使う前提で作っています。

## バックアップ

- バックアップは必ずパスワード（8 文字以上）で暗号化します。平文での書き出しはありません。
- Android 4.4 以降は、システムのファイル選択画面で保存先を選びます。4.3 以前は SD カードの `Kurakagi` フォルダに保存します。
- 1.0.0 の暗号化バックアップ（形式 v1）と、初期の試験版の平文の JSON も読み込めます。平文のファイルを読み込んだときは、削除を促します。
- PBKDF2 の回数はファイルに記録するので、回数の違うバックアップも読めます。

## 作者が確かめた範囲

| 機種 | Android | 確かめ方 | 何を確かめたか | 日付 |
|---|---|---|---|---|
| Sony Xperia acro HD（SO-03D） | 4.0.4 | 実機 | 一通り: QR と手入力での登録、コード、QR とシークレットの表示、暗号化バックアップの書き出しと読み込み。暗号化は 3〜4 秒ほど | 2026-11 |
| Vodafone VFD 200 | 5.1 | 実機 | QR の読み取り、「情報」の画面 | 2026-11 |
| Sony Xperia Z5 Compact（SO-02H） | 7.0 | 実機 | 起動、QR の読み取り | 2026-11 |
| Sony Xperia X Compact（SO-02J） | 8.0 | 実機 | 起動 | 2026-11 |
| エミュレーター | 2.3.7 | エミュレーター | 起動、手入力での登録、コード、QR の表示、SD カードへの暗号化バックアップと読み込み、違うパスワードを断ること、読み取りの画面が開くこと | 2026-11 |

実機ではまだ確かめていないもの: Android 2.x のすべて（暗号化にかかる時間、カメラでの QR の読み取りを含む）。Android 3.x は、まったく確かめていません。

## 利用者の報告

| 機種 | Android | 結果 | 報告 | 日付 |
|---|---|---|---|---|
| — | — | — | — | — |

お使いの端末で動きましたか？ Issue の「動作の報告」か、<https://drawerdev.github.io/> のフォームで知らせてください。

## ビルド

必要なもの: JDK 21 と Android SDK（`local.properties` の `sdk.dir` か、環境変数 `ANDROID_HOME` で場所を指定）。

```sh
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

APK は `app/build/outputs/apk/debug/app-debug.apk` にできます。
デバッグ版は `io.github.drawerdev.kurakagi.debug` として、リリース版と並べて入ります（リリース版の中のアカウントを上書きしません）。

`gradlew test` は `CoreTestRunner`（RFC 4226/6238、Base32、バックアップ形式 v2 と改ざんの検出、PBKDF2-HMAC-SHA256、QR など）と `ComprehensiveVerificationTest` を走らせます。
`gradlew lint` は、Android 2.1 より新しい API を版の確認なしで使うと止まります（`NewApi`）。

リリース版の署名: 鍵とパスワードはリポジトリに置きません。`~/.gradle/gradle.properties`（または同じ名前の環境変数）に次の 4 つを書きます。

```properties
KURAKAGI_KEYSTORE=/path/to/kurakagi.keystore
KURAKAGI_KEYSTORE_PASSWORD=...
KURAKAGI_KEY_ALIAS=...
KURAKAGI_KEY_PASSWORD=...
```

無いときは、`./gradlew assembleRelease` は署名していない `app-release-unsigned.apk` を作ります。署名は v1（JAR）・v2・v3 です。

## 問題の報告

設定 → 情報 の「エラー報告をコピー」を押してから（アプリの版・Android の版・機種名だけをコピーします）、
<https://github.com/drawerdev/kurakagi/issues> に書いてください。
シークレット・QR コード・アカウントの名前は、issue に貼らないでください。

## ライセンス

GPL-3.0-or-later。[LICENSE](LICENSE) と [NOTICE](NOTICE) を見てください。

QR コードの読み書きに ZXing core 2.3.0（Apache-2.0）を使っています。

## drawerdev のほかのアプリ

- Mataoto — 音楽プレイヤー兼ホーム（Android 5.0 以上）
- Mataoto Lite — Mataoto の軽量版（Android 2.1 以上）
- HauntChat — 古い端末でも使える AI チャット（Android 2.3 以上）

一覧は <https://drawerdev.github.io/> にあります。

## 応援

気に入ったら、OFUSE や GitHub Sponsors で drawerdev を応援できます。案内は <https://drawerdev.github.io/> にあります。
