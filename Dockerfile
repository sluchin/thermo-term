# 開発用のイメージ (dev)
FROM ubuntu:26.04 AS dev

ENV DEBIAN_FRONTEND=noninteractive

# ログの日本語 (Kotlin のメッセージ, KDoc の警告など) を文字化けさせない
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# JDK 17 (Android Gradle Plugin は 17 以上が必要. ホストの JDK は使わない)
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    unzip \
    curl \
    ca-certificates \
    git \
    && rm -rf /var/lib/apt/lists/*

# Gradle (ラッパー gradlew を作り直すときだけ使う. ビルドは ./gradlew で行う)
ARG GRADLE_VERSION=9.8.1
RUN curl -fL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip

# Android SDK (コマンドラインツール)
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ARG CMDLINE_TOOLS=16111833
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fL -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip && \
    unzip -q /tmp/cmdtools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdtools.zip
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

# SDK のコンポーネント. コンテナは一般ユーザーで動き, SDK は読み取り専用になる.
# 足りないコンポーネントは, Gradle が実行時にインストールできない. 必要になったら, ここに足す.
# build-tools は app/build.gradle.kts で固定する.
RUN yes | sdkmanager --licenses > /dev/null && \
    sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" && \
    chmod -R a+rX ${ANDROID_HOME}

# Gradle のキャッシュ. 名前付きボリュームを mount する (空のボリュームはこの権限を引き継ぐ)
ENV GRADLE_USER_HOME=/gradle-home
RUN mkdir -p /gradle-home && chmod 777 /gradle-home

# コンテナはホストユーザーで実行されるが, イメージ内の /workspace はそのユーザーの所有ではないため
RUN git config --system --add safe.directory '*'

WORKDIR /workspace
