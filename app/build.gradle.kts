import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// テザリングの SSID とパスワードはリポジトリに入れない.
// local.properties (コミットしない. UTF-8) か, Gradle のプロパティ (-Pthermoterm.ssid=...) で渡す.
// 渡した値は APK に平文で入る (入力欄の初期値になる). その APK を配布しない.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.reader(Charsets.UTF_8).use { load(it) }
        }
    }

/** 設定の値を, local.properties, Gradle のプロパティ, 既定値の順に探す. */
fun configString(name: String): String {
    val value = localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull ?: ""
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

// Kotlin の警告が出たら, ビルドを失敗させる (core と同じ)
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

android {
    namespace = "jp.thermoterm.app"
    // build-tools は, Docker のイメージに入っている版に固定する (SDK は読み取り専用で, 既定の版 (36.0.0) は, 入っていない)
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // applicationId は, 仮の値 (リリースの前に決める)
        applicationId = "jp.thermoterm.app"
        // USB の使用許可の PendingIntent (FLAG_MUTABLE) と, ブロードキャストの RECEIVER_NOT_EXPORTED が
        // 条件分岐なしで使える版
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "TETHER_SSID", configString("thermoterm.ssid"))
        buildConfigField("String", "TETHER_PSK", configString("thermoterm.psk"))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    // 警告も, エラーにする
    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

// APK の名前を, リポジトリの名前と版にする (例: thermo-term-0.1.0-debug.apk. 後ろに, ビルドの種類が付く)
base {
    archivesName = "thermo-term-${android.defaultConfig.versionName}"
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
