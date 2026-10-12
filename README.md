# thermo-term

スマートフォンと ESP32C3 を **USB ケーブル (Type-C)** でつなぎ、ESP32C3 の WiFi の SSID とパスワードを、スマートフォンの **WiFi テザリング (ホットスポット)** のものに書き換える Android アプリです (Kotlin)。**元の SSID とパスワードは、ESP32C3 が保存して**、あとで元に戻せます。

姉妹プロジェクト [esp32-thermo-system](https://github.com/sluchin/esp32-thermo-system) (ESP32C3 の温度センサのノードと、ゲートウェイ) の、設定用の端末 (terminal) です。データを見るアプリは、[thermon](https://github.com/sluchin/thermon) です。

```
スマートフォン (USB ホスト) ──USB Type-C──> ESP32C3 (USB シリアル/JTAG。Zephyr のシェル)
   thermo tether "<SSID>" "<パスワード>"   元の SSID とパスワードを保存して、テザリングのものに切り替える
   thermo restore                          元の SSID とパスワードに戻す
   thermo apply                            接続をやり直す
   thermo show                             今の状態 (パスワードは伏せ字)
```

**Android Studio は使いません。** ビルド、テスト、整形、静的解析、ドキュメントの生成は、全て、コマンドラインで、Docker の中で実行します ([thermon](https://github.com/sluchin/thermon) と同じ方針です)。

> **状態**: アプリ (`core` と `app`) と、ファームウェアの `thermo tether` / `thermo restore` を実装しました。単体テストは、Zephyr のシェルを真似る偽の機器で行います。**実機 (USB の通信、ESP32C3 のシェルの応答、テザリングへの接続) では、まだ確認していません** ([SETUP.md](SETUP.md))。

## 使い方

1. スマートフォンの WiFi テザリングを入れる。**2.4 GHz** と **WPA2** にする (ESP32-C3 は 5 GHz に対応しない。Android の既定が 5 GHz のことがある)。
2. ESP32C3 を、スマートフォンに、**データ通信に対応した** USB-C ケーブルでつなぐ (充電専用は不可。端末が OTG に対応していること)。
3. アプリを開く。使用許可を聞かれたら許可する。「Connect / refresh」で、今の SSID と、元の SSID が出る。
4. テザリングの **SSID とパスワードを入力** して (Android はアプリに教えないので、手で入力する。`local.properties` に書いてビルドすると、入力欄に最初から入る。下の「SSID とパスワードを、ビルドで渡す」)、「Switch to hotspot」を押す。ESP32C3 が、元の設定を保存して、テザリングに接続し直す。
5. 戻すときは、「Restore original」を押す。
6. **ログを見る**: 接続したあと「Start log」を押すと、ESP32C3 のシリアルのログ(WiFi の接続など)が、画面の下に出る。「Stop log」で止める。「Clear log」で消す(画面には、最後の 20000 文字を残す)。**コマンドを打つ**: 「Command」欄に入力して「Send」(またはキーボードの送信)を押すと、そのまま ESP32C3 のシェルに送る(例: `thermo show`)。応答はログに出る(ログが止まっていれば、自動で始める。パスワードを含むコマンドは、ログにも出るので注意)。切り替えや戻すときは、ログを自動で止める(コマンドの応答と混ざらないように)。

「USB テザリング」ではありません。XIAO ESP32C3 の USB は、デバイス側 (USB シリアル/JTAG) で、ネットを共有してもらう通信には、使えません。USB ケーブルは、設定を書き込むときだけに使い、通信は WiFi のテザリングで行います。

**`No ESP32C3 found` と出たとき**: メッセージの下に、スマートフォンから見えている USB の機器の一覧(`VID:PID` と、インターフェースのクラス)が出ます。`(none)` なら、スマートフォンが機器を見ていません(ケーブルが充電専用、USB のホストになっていない、ESP32C3 が別の電源で動いているなど)。`303A:1001 classes=[0x02,0x0A,0xFF]` が出ていれば、見えているので、アプリの側の問題です。

画面には 4 つのタブがあります。**WiFi** (SSID とパスワードの入力、切り替え、元に戻す) と、**Serial** (ログとコマンド) と、**SD log** (SD カードに保存された起動のログ)と、**BLE** (Bluetooth で温度と湿度を表示)。ログは、終わりまで見ているときは、自動でスクロールします (上へ戻して読んでいるときは、動きません)。

**BLE のタブ**: 「Connect with Bluetooth」を押すと、Thermo のノードを探してつなぎ、温度と湿度を表示します (ノードが 5 秒ごとに通知します)。USB のホストにならない機種でも使えます。ノードが接続を受け付けるのは 1 台だけなので、ゲートウェイとつながっている間は、見つかりません。Android 12 以上の「近くのデバイス」の権限を求めます。

**SD log のタブ**: 接続したあと「Load boot.log from SD」を押すと、機器のシェルの `fs cat /SD:/boot.log` で、SD カードの起動のログ (起動から 60 秒間の全てのログ。ファームウェアの `sdlog.c` が書く) を読んで、表示します。SD カードがない、またはファイルがないときは、機器のエラーの文が出ます。読むあいだは、Serial のログを止めます。

## SSID とパスワードを、ビルドで渡す

`local.properties` (コミットしない。UTF-8) に書いてビルドすると、アプリの入力欄に、最初から入ります。「Switch to hotspot」を押すと、ESP32C3 に書き込みます (押すまでは、書き込みません)。

```properties
thermoterm.ssid=my-phone
thermoterm.psk=my-password
```

`-Pthermoterm.ssid=...` `-Pthermoterm.psk=...` でも渡せます。**値は、APK に平文で入る**ので、その APK を、配布しないでください。

## パスワードの扱い

- アプリは、パスワードを**保存しません** (ビルドで渡した値は、APK の中にあります)。ログにも、画面にも出しません。
- 元のパスワードは、ESP32C3 のフラッシュに保存されます。基板の外には出ません (`thermo show` も伏せ字です)。
- 機器の応答には、入力したパスワードの反響 (エコー) が含まれるので、アプリは、応答を解析した結果のメッセージだけを表示します。

## プロジェクト構成

```
core/    純粋な Kotlin (Android に依存しない). シェルとの通信, 切り替えの手順, 入力の確認. 単体テストで, 行と分岐のカバレッジ 100%
app/     Android アプリ. USB (CDC ACM) の通信, 画面, 使用許可だけの, 薄い層
config/  detekt (静的解析) の設定
gradle/  版カタログ (libs.versions.toml) と, Gradle ラッパー
scripts/ クラウドのセッションで Docker を使う補助, Maven のミラーの設定
.github/ GitHub Actions (CI と, ドキュメントの公開)
```

## ビルド方法

Docker と Docker Compose V2 が要ります (JDK、Gradle、Android SDK は、イメージに入っています)。最初に、`UID` と `GID` をエクスポートします (`export UID; export GID=$(id -g)`)。

```bash
docker compose build              # イメージ (thermo-term-dev) を作る (最初の 1 回)
docker compose run --rm build     # デバッグ APK: app/build/outputs/apk/debug/app-debug.apk
docker compose run --rm test      # 単体テスト
docker compose run --rm coverage  # core の行と分岐が 100% でなければ失敗
docker compose run --rm format    # 整形の確認 (format-fix で、書き換える)
docker compose run --rm analyze   # 静的解析 (detekt と Android Lint)
docker compose run --rm docs      # ドキュメント (Dokka): core/build/dokka/html/index.html
docker compose run --rm clean     # 生成物を消す
```

クラウドのセッション (claude.ai/code) では、`docker compose run --rm <サービス>` の代わりに、`scripts/cloud-docker.sh run <サービス>` を使います ([SETUP.md](SETUP.md))。

実機に入れるには、ホストの adb で `adb install -r app/build/outputs/apk/debug/app-debug.apk` です。

## CI

GitHub Actions (`.github/workflows/build.yml`) が、push のたびに、同じ Docker イメージで、`build` `test` `coverage` `format` `analyze` `docs` を実行します。main への push では、ドキュメントを GitHub Pages に公開します。

## ライセンス

GPL-3.0-or-later ([LICENSE](LICENSE))。
