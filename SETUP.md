# 開発環境のセットアップ

Kotlin で Android アプリを、**Android Studio を使わずに、コマンドラインだけで**開発するための手順と、検証の記録です。ビルド、テスト、整形、静的解析、ドキュメントの生成は、Docker の中で実行します。環境は、姉妹プロジェクトの [thermon](https://github.com/sluchin/thermon) と同じです (Dockerfile、版、`scripts/`、CI)。thermon の `SETUP.md` に、より詳しい説明があります。

## 方針

| 項目 | 方針 |
|:---|:---|
| 開発環境 | Docker (Ubuntu 24.04)。JDK、Gradle、Android SDK は、イメージに入れる。ホストには、Docker と、実機に転送するための adb だけを入れる |
| 構成 | 2 つのモジュール。`core` (純粋な Kotlin。単体テストで、行と分岐のカバレッジ 100%) と、`app` (Android。薄い層) |
| 品質 | 警告ゼロ (Kotlin の警告をエラーにする)。ktlint、detekt、Android Lint (警告をエラーにする)、JaCoCo、Dokka |
| テスト | JVM 上の単体テストだけ。エミュレータは、USB のホストの動作を確認できないので、置かない |
| 実機での確認 | 別に必要 (USB の通信、ESP32C3 の応答、テザリングへの接続) |

## 使うツールと版

版は、`gradle/libs.versions.toml` にまとめてあります。

| ツール | 版 |
|:---|:---|
| JDK | 17 |
| Gradle | 9.8.1 (ラッパー) |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| Android SDK | `platforms;android-37.0`、`build-tools;37.0.0` (`compileSdk` 37、`minSdk` 33) |
| ktlint / detekt | プラグイン 14.2.0 / 1.23.8 |
| JaCoCo / Dokka | 0.8.15 / 2.2.0 |
| JUnit / kotlin-test | 6.1.3 / 2.4.21 |
| kotlinx.coroutines | 1.11.0 |

`minSdk` を 33 (Android 13) にしたのは、USB の使用許可の `PendingIntent.FLAG_MUTABLE` と、`registerReceiver` の `RECEIVER_NOT_EXPORTED` を、版の分岐なしで使うためです。

## Docker のセットアップ

```bash
export UID; export GID=$(id -g)     # コンテナを、ホストのユーザーで実行する (生成物が、root 所有にならない)
docker compose build                # イメージ thermo-term-dev:latest を作る (最初の 1 回と、Dockerfile を変えたとき)
docker compose run --rm dev         # 開発シェル
```

| サービス | 内容 |
|:---|:---|
| `build` | デバッグ APK (`app/build/outputs/apk/debug/app-debug.apk`) |
| `test` | 単体テスト |
| `coverage` | `core` の行と分岐が 100% でなければ失敗 |
| `format` / `format-fix` | 整形の確認 (ktlint) / 書き換え |
| `analyze` | 静的解析 (detekt と Android Lint)。指摘があれば失敗 |
| `docs` | Dokka の HTML (`core/build/dokka/html/index.html`)。警告があれば失敗 |
| `clean` | `build/` `.gradle/` `.kotlin/` を消す (Gradle のキャッシュは消さない) |

## クラウドのセッション (claude.ai/code)

通常の `docker compose build` と `docker compose run` は、プロキシと CA の設定がなく、curl の終了コード 60 や、Maven Central の 429 で失敗します。`scripts/cloud-docker.sh` を使います (thermon と同じスクリプト)。

```bash
scripts/cloud-docker.sh build        # イメージを作る (プロキシと CA を足す)
scripts/cloud-docker.sh run <サービス>  # build / test / coverage / format / format-fix / analyze / docs
scripts/cloud-docker.sh start        # Docker のデーモンを起動する
```

通信できるドメインは、環境の「Network access」で決まります (`dl.google.com` と `maven-central.storage-download.googleapis.com` が要ります)。詳しくは、thermon の `SETUP.md` と `.claude/CLAUDE.md` です。

## SSID とパスワードの設定

`local.properties` (コミットしない) の `thermoterm.ssid` と `thermoterm.psk`、または Gradle のプロパティ (`-Pthermoterm.ssid=...`) で渡すと、`BuildConfig.TETHER_SSID` と `TETHER_PSK` になり、入力欄の初期値になります。書き込みは、ボタンを押したときです。値は APK に平文で入るので、配布しません。

## CI (GitHub Actions)

`.github/workflows/build.yml`。push (`main`、`ccr-*`) と PR で、開発用のイメージ (レイヤーを Actions のキャッシュに保存) を作り、`build` `test` `coverage` `format` `analyze` `docs` を実行します。ログは、アーティファクト (`<サービス名>_log`) に保存します (APK は、保存しません)。`docs` は、main への push のときだけ、GitHub Pages に公開します (Settings > Pages > Source を「GitHub Actions」にしておく)。`cleanup` が、3 日より前の実行を、削除します。**CI は、まだ実行していません** (リポジトリに、push してから確認する)。

## アプリの仕組み

| 場所 | 内容 |
|:---|:---|
| `core` の `SerialPort` | 送信と受信 (待ち時間つき) のインターフェース。Android の実装は `app` の `UsbCdcPort` |
| `core` の `ShellSession` | 行を送り、プロンプト (`uart:~$ `) が現れるまで受け取る。画面の制御 (色) は取り除く。溜まっている出力は、送る前に捨てる |
| `core` の `TetherController` | `thermo tether "<SSID>" "<パスワード>"` → `thermo apply` → `thermo show`。戻すときは `thermo restore` → `thermo apply` → `thermo show` |
| `core` の `WifiSettings.kt` | SSID (32 バイト以下) とパスワード (64 バイト以下) の確認。`thermo show` の解析 |
| `core` の `streamLog` | ログを読み続けて、文字列で渡す(止めるのは取り消し)。文字の途中で切れた受信は、続きを待つ。色と CR は取り除く。`ShellSession` と同時には使わない |
| `app` の `UsbCdcPort` | CDC のデータのインターフェース (クラス 0x0A) のバルク転送。制御線 (DTR と RTS) は、動かさない |
| `app` の `MainActivity` | 機器の検出 (ベンダー ID `0x303A`)、使用許可、接続。USB を抜いたら、切断の表示。ログの開始と停止 |

ファームウェア (esp32-thermo-system) の `thermo tether <SSID> [パスワード]` は、今の SSID とパスワードを、フラッシュの 1 つの項目 (`tether_bak`) に保存してから、新しい値を設定します。すでに切り替えているときは、最初の元の設定を残します。`thermo restore` は、戻して、保存した元の設定を消します。

## 検証の記録 (2026-10-12、クラウドのセッション)

- 確認した: `format-fix` `format` `analyze` `build` `test` `coverage` (`core` の行と分岐が 100%) `docs`。全て成功。
- **未確認**: 実機での USB の通信 (使用許可、CDC ACM のバルク転送。制御線を動かさなくても、シェルが応答するか)、Zephyr のシェルの引用符 (`\"` と `\\`) の扱い、日本語の SSID、テザリングへの接続、CI。
