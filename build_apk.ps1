# Kurakagi 完全自動ビルドスクリプト (API 7 minSdk, API 34 targetSdk)
$ErrorActionPreference = "Stop"

# Android SDK: 環境変数 ANDROID_HOME。無ければ Android Studio の既定の場所
$SDK = $env:ANDROID_HOME
if (-not $SDK) { $SDK = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$BUILD_TOOLS = "$SDK\build-tools\35.0.0"
$ANDROID_JAR = "$SDK\platforms\android-34\android.jar"
# このスクリプトがあるフォルダ
$PROJECT_ROOT = $PSScriptRoot
$APP_ROOT = "$PROJECT_ROOT\app"
$BUILD_DIR = "$PROJECT_ROOT\build"
$OUT_DIR = "$PROJECT_ROOT\dist"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Kurakagi APK ビルドプロセス開始        " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. 出力ディレクトリ初期化
Remove-Item -Recurse -Force $BUILD_DIR -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $BUILD_DIR\res_compiled | Out-Null
New-Item -ItemType Directory -Force -Path $BUILD_DIR\gen | Out-Null
New-Item -ItemType Directory -Force -Path $BUILD_DIR\classes | Out-Null
New-Item -ItemType Directory -Force -Path $OUT_DIR | Out-Null

# 2. aapt2 compile でリソースをコンパイル (*.flat 生成)
Write-Host "[1/7] aapt2 によるリソースコンパイル中..." -ForegroundColor Yellow
& "$BUILD_TOOLS\aapt2.exe" compile --dir "$APP_ROOT\src\main\res" -o "$BUILD_DIR\res_compiled"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# 3. aapt2 link で R.java の生成およびリソース APK の生成
Write-Host "[2/7] aapt2 によるリンク処理 (R.java 生成)..." -ForegroundColor Yellow
$flatFiles = Get-ChildItem -Filter *.flat "$BUILD_DIR\res_compiled" | ForEach-Object { $_.FullName }
& "$BUILD_TOOLS\aapt2.exe" link `
    -I "$ANDROID_JAR" `
    --manifest "$APP_ROOT\src\main\AndroidManifest.xml" `
    --java "$BUILD_DIR\gen" `
    -o "$BUILD_DIR\resources.apk" `
    --min-sdk-version 7 `
    --target-sdk-version 34 `
    --version-code 2 `
    --version-name "1.0.1" `
    --auto-add-overlay `
    $flatFiles
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$CORE_JAR = "$APP_ROOT\libs\core-2.3.0.jar"
if (-not (Test-Path $CORE_JAR)) {
    throw "core-2.3.0.jar not found at $CORE_JAR"
}

# 4. javac で Java ソースコードをコンパイル (Java 8 互換バイトコード)
Write-Host "[3/7] javac による Java コードコンパイル (Java 8 バイトコード)..." -ForegroundColor Yellow
$javaSources = (Get-ChildItem -Recurse -Filter *.java "$APP_ROOT\src\main\java").FullName + `
               (Get-ChildItem -Recurse -Filter *.java "$BUILD_DIR\gen").FullName

$sourcesFile = "$BUILD_DIR\sources_to_compile.txt"
[System.IO.File]::WriteAllLines($sourcesFile, $javaSources, [System.Text.Encoding]::ASCII)

javac -encoding UTF-8 `
    -source 1.8 -target 1.8 `
    -g:source,lines `
    -bootclasspath "$ANDROID_JAR" `
    -cp "$ANDROID_JAR;$CORE_JAR" `
    -d "$BUILD_DIR\classes" `
    "@$sourcesFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$JAR_EXE = "C:\Program Files\Java\jdk-21.0.12\bin\jar.exe"
$KEYTOOL_EXE = "C:\Program Files\Java\jdk-21.0.12\bin\keytool.exe"

# 5. d8 による Dalvik DEX 変換 (min-api 7, desugaring 適用)
Write-Host "[4/7] d8 による DEX 生成 (min-api 7)..." -ForegroundColor Yellow
$appJar = "$BUILD_DIR\app.jar"
& "$JAR_EXE" cf "$appJar" -C "$BUILD_DIR\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar creation failed" }

& "$BUILD_TOOLS\d8.bat" --min-api 7 --lib "$ANDROID_JAR" --output "$BUILD_DIR" "$appJar" "$CORE_JAR"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# 6. resources.apk に classes.dex を追加 & zipalign
Write-Host "[5/7] classes.dex を APK に格納 & zipalign..." -ForegroundColor Yellow
Copy-Item "$BUILD_DIR\resources.apk" "$BUILD_DIR\unaligned.apk" -Force

# aapt add で classes.dex を追加
Push-Location $BUILD_DIR
& "$BUILD_TOOLS\aapt.exe" add "unaligned.apk" "classes.dex"
Pop-Location
if ($LASTEXITCODE -ne 0) { throw "aapt add failed" }

# zipalign (4バイト境界整列)
& "$BUILD_TOOLS\zipalign.exe" -f -p 4 "$BUILD_DIR\unaligned.apk" "$BUILD_DIR\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# 7. apksigner による署名 (v1 JAR署名 + v2 署名)
Write-Host "[6/7] キーストア確認 & apksigner による署名 (v1 + v2 + v3)..." -ForegroundColor Yellow
# 署名鍵とパスワードはソースに置かない。
#   鍵の場所:   環境変数 KURAKAGI_KEYSTORE。無ければ %USERPROFILE%\keys\kurakagi.keystore（Mataoto の鍵と同じフォルダ）
#   パスワード: 環境変数 KURAKAGI_KS_PASS。無ければ実行時に入力を求める
#   鍵の別名(alias): retroauth。2026-11-13 に作った鍵は改名前の名前で作ったため、そのまま使う（外には出ない）
$keyAlias = "retroauth"
$keystore = $env:KURAKAGI_KEYSTORE
if (-not $keystore) { $keystore = Join-Path $env:USERPROFILE "keys\kurakagi.keystore" }
# ソースのフォルダに鍵があっても使わない（出所の分からない古い鍵。2026-11-13 に廃止）
if (Test-Path "$PROJECT_ROOT\*.keystore") {
    throw "ソースのフォルダに .keystore があります。使わないので、フォルダの外へ移してください。"
}

function Read-Plain([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

$creating = -not (Test-Path $keystore)
if (-not $env:KURAKAGI_KS_PASS) {
    if ($creating) {
        Write-Host "署名鍵を新しく作ります。パスワードを決めてください（12 文字以上）。" -ForegroundColor Green
        Write-Host "このパスワードと鍵のファイルを失うと、アプリを二度と更新できません。" -ForegroundColor Green
        $p1 = Read-Plain "新しいパスワード"
        $p2 = Read-Plain "もう一度"
        if ($p1 -ne $p2) { throw "2 つのパスワードが一致しません" }
        if ($p1.Length -lt 12) { throw "12 文字以上にしてください" }
        $env:KURAKAGI_KS_PASS = $p1
    } else {
        $env:KURAKAGI_KS_PASS = Read-Plain "署名鍵のパスワード"
    }
}

if ($creating) {
    Write-Host "署名鍵を新しく作ります: $keystore" -ForegroundColor Green
    New-Item -ItemType Directory -Force -Path (Split-Path $keystore) | Out-Null
    # 証明書には作者名だけを入れる（地名などの手掛かりを入れない）
    & "$KEYTOOL_EXE" -genkeypair -v -keystore $keystore -alias $keyAlias -keyalg RSA -keysize 4096 -validity 10000 `
        -storepass:env KURAKAGI_KS_PASS -keypass:env KURAKAGI_KS_PASS -dname "CN=drawerdev"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
    Write-Host "[大事] 鍵のファイルとパスワードを、別の場所（USB メモリなど）にも保管してください: $keystore" -ForegroundColor Red
}

$RELEASE_APK = "$OUT_DIR\Kurakagi-release.apk"
# build/ の中で署名し、成功したときだけ dist/ へ置く（失敗しても前の署名済み APK を壊さない）
$SIGNED_TMP = "$BUILD_DIR\signed.apk"
Copy-Item "$BUILD_DIR\aligned.apk" $SIGNED_TMP -Force

& "$BUILD_TOOLS\apksigner.bat" sign --ks "$keystore" --ks-pass env:KURAKAGI_KS_PASS --ks-key-alias $keyAlias --key-pass env:KURAKAGI_KS_PASS --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true "$SIGNED_TMP"
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }
Move-Item $SIGNED_TMP $RELEASE_APK -Force
Remove-Item "$BUILD_DIR\signed.apk.idsig" -ErrorAction SilentlyContinue

# 署名検証
Write-Host "[7/7] 署名と API 7 適合性の検証中..." -ForegroundColor Yellow
& "$BUILD_TOOLS\apksigner.bat" verify --verbose "$RELEASE_APK"

Write-Host "==========================================" -ForegroundColor Green
Write-Host "   ビルド成功！APK生成完了:               " -ForegroundColor Green
Write-Host "   $RELEASE_APK" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Get-Item $RELEASE_APK | Select-Object Name, Length, LastWriteTime
