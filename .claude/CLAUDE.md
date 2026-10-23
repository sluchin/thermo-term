# プロジェクト設定

Kotlin で作る Android アプリ。スマートフォンと ESP32C3 (姉妹プロジェクト [esp32-thermo-system](https://github.com/sluchin/esp32-thermo-system) のノードかゲートウェイ) を USB ケーブル (Type-C) でつなぎ、ESP32C3 の WiFi の SSID とパスワードをスマートフォンの WiFi テザリング (ホットスポット) のものに書き換える。**元の SSID とパスワードは ESP32C3 が保存して**、あとで元に戻せる。

```
スマートフォン (USB ホスト) ──USB Type-C──> ESP32C3 (USB シリアル/JTAG = CDC ACM。Zephyr のシェル)
   thermo tether "<SSID>" "<パスワード>" → thermo apply    (元の設定を保存して、テザリングに切り替える)
   thermo restore → thermo apply                           (元の SSID とパスワードに戻す)
   thermo show                                              (今の状態。パスワードは伏せ字)
```

**Android Studio は使わない。** ビルド、テスト、整形、静的解析、ドキュメントの生成は全てコマンドラインで、Docker の中で実行する。環境構築の詳細と、検証の記録は `SETUP.md`。

## コマンド

> **クラウドのセッション (claude.ai/code。スマホのアプリを含む。環境変数 `CLAUDE_CODE_REMOTE` が `true`) では、下の `docker compose run --rm <サービス>` を使わず、`scripts/cloud-docker.sh run <サービス>` で実行する** (`build`、`test`、`coverage`、`format`、`format-fix`、`analyze`、`docs`)。通常の `docker compose` はプロキシの設定がないため、curl の終了コード 60 や、Maven Central の 429 で失敗する。詳しくは下の「クラウドのセッション (claude.ai/code) で Docker を使うとき」。

### Docker (推奨)
- `docker compose run` には必ず `--rm` を付ける (終わったコンテナを残さないため)。
- `UID` と `GID` をシェルでエクスポートしておく (生成物が root 所有にならないように。`SETUP.md` の「2. ホストのユーザー ID を環境変数にエクスポートする」)。
- イメージのビルド: `docker compose build` (`Dockerfile` を変えたときも)
- ビルド (デバッグ APK): `docker compose run --rm build` (`app/build/outputs/apk/debug/thermo-term-<版>-debug.apk`)
- 単体テスト: `docker compose run --rm test`
- カバレッジ (`core` の行と分岐が 100%): `docker compose run --rm coverage`
- 整形の確認: `docker compose run --rm format` / 整形して書き換える: `docker compose run --rm format-fix`
- 静的解析 (detekt と Android Lint): `docker compose run --rm analyze`
- ドキュメント (Dokka): `docker compose run --rm docs` (`core/build/dokka/html/index.html`)
- クリーン: `docker compose run --rm clean` (`build/`、`.gradle/`、`.kotlin/` を消す。Gradle のキャッシュは消さない)
- 開発シェル: `docker compose run --rm dev`

### 実機への転送 (ホストの adb)
- `adb install -r app/build/outputs/apk/debug/thermo-term-*-debug.apk` (ホストに adb を入れる。`SETUP.md` の「実機へのインストール」)

### クラウドのセッション (claude.ai/code) で Docker を使うとき

**通常の `docker compose build` と `docker compose run` は使わない** (curl の終了コード 60 で失敗する。理由は下)。代わりに、`scripts/cloud-docker.sh` を使う (`SETUP.md` の「クラウドのセッション」)。

```bash
scripts/cloud-docker.sh build          # 開発用のイメージ (thermo-term-dev) を作る (プロキシと CA の設定を足す)
scripts/cloud-docker.sh run build      # docker compose run --rm build (test、coverage、format、analyze、docs も同じ)
scripts/cloud-docker.sh start          # Docker のデーモンを起動する (起動済みなら、何もしない)
```

- クラウドの VM には Docker (`dockerd`) が入っているが、デーモンは自動では起動しない。SessionStart フック (`.claude/settings.json`) がセッションの開始のときに、`scripts/cloud-docker.sh hook` で起動する (クラウドのときだけ)。起動していないときは `scripts/cloud-docker.sh start`。ログは `/tmp/dockerd.log`。
- VM の外への通信は全てAnthropic のプロキシを通る。通信できるドメインは環境の「Network access」で決まる (標準の Trusted の一覧にないドメインは「Custom」で足す。`dl.google.com` と `maven-central.storage-download.googleapis.com` が要る)。
- **curl の終了コード 60 の原因** (2026-10-11 に確認. thermon で): VM の通信はプロキシと、そのプロキシの CA 証明書を使うが、**コンテナはどちらも引き継がない**。
  - VM には `HTTPS_PROXY=http://127.0.0.1:<ポート>` が設定されている。**ポートはセッションの途中でも変わる** (例: 37451、45999) ので、固定で書かず、実行のたびに `$HTTPS_PROXY` から読む。`127.0.0.1` はコンテナの中では、コンテナ自身を指す。
  - CA 証明書は `/root/.ccr/ca-bundle.crt` にある (`SSL_CERT_FILE` などが指す)。VM のシステムの信頼ストアには入っていない。
- **`cloud-docker.sh` がしていること**: `Dockerfile` を一時的にコピーして (リポジトリは変えない)、CA を**証明書ごとに 1 ファイルに分けて**、信頼ストアに入れる行を足す (束を 1 ファイルのまま入れると、Java の信頼ストアには最初の 1 枚しか入らず、Gradle が PKIX のエラーで落ちる)。`--network host` を付け、プロキシを `ARG` で渡す (`ENV` には焼き込まない)。**プロキシは HTTPS (`curl`、Java) だけに渡し、`HTTP_PROXY` は渡さない** (`apt` は Ubuntu のミラーに HTTP で接続するので、`HTTP_PROXY` を渡すと、プロキシに拒否されて失敗した。渡さなければ、プロキシを通さずに、直接つながる)。`apt` がそれでも失敗するときは環境の「Allowed domains」に `archive.ubuntu.com` と `security.ubuntu.com` を足す。実行 (`run`) のときは `network_mode: host` と、プロキシの環境変数と、`JAVA_TOOL_OPTIONS` (Java のプロキシ) を足す。コンテナは root で動く (`build/` などは root 所有になる)。
- イメージに入った CA はセッションが変わって、プロキシの CA が変わったときは古くなる。TLS のエラーが出たら、`scripts/cloud-docker.sh build` で作り直す。
- **Maven Central の `429 Too Many Requests`** (共有の出口の IP が回数制限を受ける)。Google のミラー `maven-central.storage-download.googleapis.com` を先に参照する。`scripts/cloud-docker.sh run` がクラウドのときに、毎回、`scripts/maven-mirror.init.gradle.kts` を Gradle のホーム (`/var/cache/thermo-term-gradle`。`GRADLE_HOME_DIR` で変えられる) の `init.d/` に置く (環境変数も、セットアップスクリプトも、要らない)。ミラーに届かないときは `WARNING` を出す (「Allowed domains」にミラーのドメインを足す)。初期化スクリプトを貼り付けで作ると、全角スペースが混ざって壊れたことがあるので、リポジトリのファイルにした。
- **クラウドでの結果** (2026-10-12): `scripts/cloud-docker.sh build` と、`run` の `format-fix` `format` `analyze` `build` `test` `coverage` `docs` が全て成功した。`scripts/maven-mirror.init.gradle.kts` は thermon と同じ (ミラー、`google()`、Plugin Portal の順で、Maven Central の 429 を避ける。ktlint のプラグインは Portal にしかない)。
- **Docker Hub の `429`** (ベースのイメージ `ubuntu:26.04` の取得): `scripts/cloud-docker.sh build` が失敗したときは Google のミラー `mirror.gcr.io/library/ubuntu:26.04` から取って、`ubuntu:26.04` のタグを付けてから、ビルドする (スクリプトが自動でする。)。
- 同じ失敗を繰り返さない。Docker を使わずに、VM の上で、直接ビルドする方法も、ある。

## プロジェクト構成

- `core/`: 純粋な Kotlin (Android に依存しない)。機器のシェルとの通信 (`SerialPort`、`ShellSession`)、`thermo tether` / `restore` / `apply` / `show` の流れ (`TetherController`)、SSID とパスワードの入力の確認と `thermo show` の解析 (`WifiSettings.kt`)、引数の引用符 (`ShellText.kt`)、ログの読み取り (`LogStream.kt`。`ShellSession` と同じポートを同時に読まない)。**単体テストで、行と分岐のカバレッジ 100%。**
- `app/`: Android。USB の CDC ACM の通信 (`UsbCdcPort`)、画面 (`TetherScreen`)、使用許可と接続 (`MainActivity`) だけの、薄い層 (UI は単体テストの対象外)。
- `gradle/libs.versions.toml`: ライブラリとプラグインの版 (版カタログ)。版を変えるときはここだけ。
- `config/detekt/detekt.yml`: detekt の規則 (既定に上書きするものだけ)。
- `Dockerfile` / `docker-compose.yml` / `scripts/` (クラウドのセッションで Docker を使う補助 `cloud-docker.sh`、Maven のミラーの設定): 開発環境。`.github/`: GitHub Actions。`SETUP.md`: 環境構築と、検証の記録。
- ファームウェア側の `thermo tether` と `thermo restore` は esp32-thermo-system の `app/common/cfg_shell.c` と `cfg.c` にある。**元の SSID とパスワードはファームウェアがフラッシュに保存する** (パスワードは基板の外に出ない)。

## 秘密情報

- **WiFi のパスワードをアプリに保存しない。ログに出さない。リポジトリに入れない。** 入力欄はパスワード用の入力にする。署名の鍵 (`*.jks`、`*.keystore`) も、コミットしない。

- テザリングの SSID とパスワードは `local.properties` の `thermoterm.ssid` と `thermoterm.psk` (または `-P`) で渡す。`BuildConfig` に入り、入力欄の初期値になる (APK に平文で入るので、配布しない)。リポジトリには実際の値を書かない。

## ログ

- ログは `AppLog` (`AppLog.kt`) だけを使う (`android.util.Log` を直接呼ばない)。タグは、ファイルごとの `private const val TAG` (機能の名前)。
- `AppLog.d` はデバッグ版だけ出す (判定は、関数の中の `BuildConfig.DEBUG`。引数は、文字列を返す関数にして、リリース版では文字列も作らない)。`AppLog.i` (最低限の情報)、`AppLog.w` (失敗したが続けられる)、`AppLog.e` (利用者の操作が失敗した) は、リリース版でも出す。
- パスワード (テザリングの PSK など) と、機器への命令と応答は、どのレベルでも書かない (`AppLog` は、機器のログを表示する `LogPanel` とは別)。ログの文字は英語。
- `AppLog` は logcat に加えて、アプリのファイル (`files/logs/app.log`。1 MB で切り替えて、古いものを 3 つ残す) にも書く (`ThermoTermApp` の `AppLog.init`。書くのは別のスレッド 1 本)。ファイルの書き方と読み方 (`RotatingLogFile`、`formatLogEntry`、`parseLog`) は `core` の `LogFile.kt` (thermon と同じ)。
- 落ちたときの例外も、`AppLog.init` がファイルに書いてから、Android の通常の処理に渡す。
- ログはアプリのメニューの「App log」(`AppLogActivity`) で見る。重大度で絞り込み、保存 (Android の保存の画面。Google Drive も選べる)、共有 (メールなど)、消すことができる。ファイルのログも、外に出るので、書かないものの決まりは同じ。

## コーディング規約

ktlint (Kotlin 公式のスタイル) と、detekt (既定の規則) に従う。迷ったら、ツールが正しい。Kotlin 公式のコーディング規約の日本語訳と、このプロジェクトでの違いは `CODING_STYLE.md`。

- **インデントは空白 4 つ。1 行は 100 桁まで** (`.editorconfig`)。整形は `format-fix` が行う (手で、整形しない)。
- **ファイルの先頭に著作権とライセンスを書く**:

  ```kotlin
  /*
   * Copyright (c) 2026 Tetsuya Higashi
   *
   * SPDX-License-Identifier: GPL-3.0-or-later
   */
  ```
- **コメントと KDoc は日本語。** 句読点は「, 」(カンマと空白) と「.」(ピリオド) にする (「、」と「。」は使わない)。ログのメッセージ、例外のメッセージ、識別子は英語。
- **公開 (public) の宣言には KDoc を付ける** (`docs` が `Undocumented` の警告で失敗する)。`@param`、`@return`、`@throws` を書く。`private` にも、意味と単位が分かる説明を付ける。
- **KDoc の中で、`[s]` や `[%]` のように、角括弧で単位を書かない** (リンクとして解釈されて、`docs` が失敗する)。`(s)` や `(%)` と書く。リンクにしたいときだけ、`[クラス名]` と書く。
- **マジックナンバーを使わない** (detekt の `MagicNumber`)。時間、倍率、上限などは `private const val` で名前を付ける。
- **警告をゼロにする。** Kotlin の警告はエラーになる (`allWarningsAsErrors`)。Android Lint も、警告をエラーにする。警告を抑える (`@Suppress`、`lint { disable }`) ときは理由をコメントに書く。
- **例外**: 汎用の例外 (`Exception`、`Throwable`) を握りつぶさない。機器との通信の失敗 (`ShellException`、`IOException`) は呼び出し元に伝える。画面で、まとめて、扱うときは `runCatching` でメッセージを表示する。
- **USB の通信 (`UsbCdcPort`) は `app` に置き、`core` は `SerialPort` というインターフェースにだけ依存する** (テストで、偽の機器 `FakeDevice` に置き換えるため)。時刻など、外の状態を使うものも、引数で渡す。
- **パスワードを保存しない、ログに出さない、画面に出さない。** 機器の応答にはパスワードの反響 (エコー) が含まれるので、応答をそのまま表示しない (表示するのは解析した結果のメッセージだけ)。
- 変更しない値は `val` にする (`var` は必要なときだけ)。`!!` はテスト以外で、使わない。

## 開発フロー

1. Push 時に GitHub Actions (`.github/workflows/build.yml`) が開発用の Docker イメージ (ローカルと同じ環境) で `docker compose` のサービスを実行する (`SETUP.md` の「CI (GitHub Actions)」)。
   - `check` ジョブ: `build` / `test` / `coverage` / `format` / `analyze` をサービスごとに実行する。失敗したら、ジョブが失敗する。実行のログをアーティファクト (`<サービス名>_log`。`build_log` など) として保存する (失敗したときも保存する)。**APK はアーティファクトに置かない。**
   - `docs` ジョブ: `docs` (Dokka の HTML。警告があれば失敗する)。生成したドキュメントをアーティファクト `docs` と、ログ `docs_log` に保存する。
   - `deploy-docs` ジョブ: main への push のときだけ、ドキュメントを GitHub Pages に公開する (リポジトリの Settings > Pages > Source を「GitHub Actions」にしておく)。
   - `cleanup` ジョブ: 3 日より前の実行 (ログと artifact) を push のたびに削除する (権限は `actions: write`)。artifact は `retention-days: 3` で 3 日で期限が切れる。保存する期間を変えるときは `build.yml` の `KEEP_DAYS` と、全ての `retention-days` をそろえて変える。
   - Docker イメージはレイヤーを GitHub Actions のキャッシュに保存する (`.github/actions/docker-image`)。Gradle の依存関係も、キャッシュに保存する (`.github/actions/gradle-cache`。`GRADLE_HOME_DIR`)。
   - アーティファクトの名前は小文字のスネークケースにする。

### コード修正時のルール

- クラウドのセッションでは、次の `docker compose run --rm <サービス>` を `scripts/cloud-docker.sh run <サービス>` に読み替える。
- コードを修正したら、次の順に全て成功することを確認する。**どれも、警告を出さない。** 警告やエラーが出たら修正して、出なくなるまで繰り返す。
  1. `docker compose run --rm format-fix` (整形。自動で直せない指摘があると、終了コード 1 になる)
  2. `docker compose run --rm format` (整形の確認)
  3. `docker compose run --rm analyze` (静的解析)
  4. `docker compose run --rm test` (単体テスト)
  5. `docker compose run --rm coverage` (`core` の行と分岐が 100%)
  6. `docker compose run --rm build` (APK のビルド)
  7. `docker compose run --rm docs` (ドキュメント。KDoc を変えたときは必ず)
- ビルドや、テストを実行できなかったときはそのことを報告する (成功したとは書かない)。
- **例外**: 変更が `SETUP.md`、`README.md`、`.claude/CLAUDE.md` などの文書だけの場合はビルドしない。
- ビルドやコマンド、ツールの版を変えたら、`SETUP.md` も更新する (版は実際に使うものだけを書く)。
- ファイルを追加したら、Gradle の設定 (`build.gradle.kts`) と、必要なら、`SETUP.md` の構成も更新する。

### タスク完了時の動作

- 修正が完了し、全ての確認が成功したら、`git diff --stat` で差分を確認し、変更点と理由の簡潔なまとめをユーザーに報告する。
- ユーザーに依頼されない限り、`git commit` / `git push` は行わない。

## テスト

単体テストは JVM 上で実行する (JUnit 5 と `kotlin-test`)。機器との通信 (`SerialPort`) は Zephyr のシェルを真似る偽の機器 (`FakeDevice`) に置き換える。中断関数 (`suspend`) は `kotlinx-coroutines-test` の `runTest` でテストする。エミュレータや、実機での自動テスト (instrumented test) は使わない。

- **配置**: `core/src/test/kotlin/` (テスト対象と、同じパッケージ)。クラス名は `<対象>Test`。`app` にはテストを置かない (薄い層にする)。
- **カバレッジ**: `core` の行と分岐は 100% (JaCoCo の `jacocoTestCoverageVerification`)。ソースを足したり、関数を変えたりしたら、対応するテストを足す (正常系と、エラーの伝わり方)。到達しない分岐を作らない (到達しない `?: return` などを書かない)。
- **テストの名前**: `<対象の関数名><条件>` をキャメルケースで (例: `resolveFailsWithoutIdentityId`)。KDoc で何を確認するかを書く。
- **時刻と乱数**: 外の状態は引数で渡して、固定する (今の `core` は時刻を使わない。受信の待ち時間も、回数で数える)。実際の時計を使う動作は別に 1 つだけ、テストする。
- **実機での確認は別に必要**: USB の通信 (使用許可、CDC ACM のバルク転送、ESP32C3 のシェルの応答)、WiFi のテザリングへの接続、画面の表示は単体テストでは、確認できない。

## 落とし穴 (検証で分かったこと。詳細は SETUP.md)

- `compileSdk` は 37 (thermon と同じ SDK のイメージを使う)。`buildToolsVersion` は `37.0.0` に固定する (コンテナの SDK は読み取り専用)。足りない SDK のコンポーネントは `Dockerfile` に足して、イメージを作り直す。
- `.gitignore` は `*.jar` を無視する。Gradle ラッパーの `gradle-wrapper.jar` だけ、例外にしてある。
- 未使用の変数は Kotlin のコンパイラの警告にならない。
- ktlint の `format-fix` は 1 回目に自動で直せない指摘があると、終了コード 1 で終わる。直したあとに、`format` で確認する。
- 画面 (`app`) を作るときはシステムバーの余白 (edge-to-edge。`targetSdk` 35 以上で必須) をパディングに入れる (入れないと、文字がアクションバーの下に隠れる)。画面は実機で確認する (エミュレータは USB のホストの動作を確認できないので、置いていない)。
- ログの日本語が `????` と化けるときはコンテナの `LANG` (`Dockerfile` の `C.UTF-8`) を確認する。
- ESP32C3 の USB (USB シリアル/JTAG) は CDC ACM (ベンダー ID `0x303A`、プロダクト ID `0x1001`)。アプリは制御線 (DTR と RTS) を動かさない (基板が再起動したり、ブートローダに入ったりするため)。シェルのプロンプトは `uart:~$ ` (Zephyr の既定)。
- Zephyr のシェルの引数は二重引用符で囲み、`\"` と `\\` で打ち消す (`quoteShellArgument`)。**実機では、まだ確認していない。**
- テザリングは**2.4 GHz** (ESP32-C3 は 5 GHz に対応しない) と WPA2-PSK にする。Android の「USB テザリング」は使えない (基板が USB のホストになれない)。使うのは WiFi テザリング。

## コミットガイドライン

- コミットメッセージは英語で記述
- **件名は Conventional Commits の形式で書く**: `type(scope): description`。scope (括弧を含む) は省略できる。description は英語で、命令形 (例: `feat(core): Add the parser of the shell reply`)。
  - type は次のどれか。
    - `feat`: 機能の追加
    - `fix`: バグの修正
    - `docs`: 文書とコメントだけの変更 (`*.md`、KDoc)
    - `style`: 整形、規約の適用 (動作を変えない)
    - `refactor`: 動作を変えない、書き換え
    - `test`: テストだけの変更
    - `build`: ビルドと、開発環境 (`build.gradle.kts`、`gradle/libs.versions.toml`、`Dockerfile`、`docker-compose.yml`、Gradle ラッパー) の変更
    - `ci`: CI (`.github/`) の変更
    - `chore`: そのほかの雑務 (最初のコミット、`.gitignore` など)
  - scope は変更の場所: `core` / `app` (そのモジュールだけ)、`docker`、`gradle`。両方のモジュールにまたがるときは省略する。
  - 互換性を壊す変更は type (または scope) の直後に感嘆符を付ける (Conventional Commits の仕様のとおり)。
  - 複数の type にまたがるときは変更の中心になる type にする。
- 件名のあとに空行を 1 行入れて、本文を書く。本文はダッシュ/ハイフンで始まる箇条書き (例: `- Fix bug in parser`)
- コミットメッセージに `Co-Authored-By` または Claude 帰属行を含めない
- **rebase したあとの push は `git push --force-with-lease --force-if-includes origin <ブランチ名>` で行う** (`--force` は使わない。リモートに手元に取り込んでいないコミットがあれば、push は失敗する)。ユーザーに依頼されたときだけ、push する。
