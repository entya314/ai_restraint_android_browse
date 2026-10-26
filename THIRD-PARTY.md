# Third-party notices

The APK uses open source software under the Apache License, Version 2.0. A copy is in `licenses/Apache-2.0.txt`. AndroidX modules and ZXing dependencies are resolved by Gradle; the full build dependency list can be obtained with `gradlew :app:dependencies --configuration debugRuntimeClasspath`.

- AndroidX WebKit 1.12.1 and its AndroidX dependencies — Copyright The Android Open Source Project. https://android.googlesource.com/platform/frameworks/support/
- ZXing Android Embedded 4.3.0 — Copyright Journey Mobile, Inc. and contributors. https://github.com/journeyapps/zxing-android-embedded
- ZXing Core 3.4.1 — Copyright ZXing authors. https://github.com/zxing/zxing
- Gradle Wrapper 8.11.1 (source distribution build tooling) — Copyright Gradle, Inc. https://github.com/gradle/gradle

JUnit and AndroidX Test are used only by test programs. Playwright 1.56.1 (Apache-2.0) is used only by the separate development tests. They are not included in the main application APK.
