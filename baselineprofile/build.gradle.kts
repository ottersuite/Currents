plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "app.otter.client.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // BaselineProfileRule needs the profile-collection support that landed in API 28. The
        // app itself still ships to 26; only the generator has the higher floor.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // Generation runs against whatever device is attached; there is no managed-device image
    // pinned here, so `./gradlew :app:generateBaselineProfile` needs a phone or emulator.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}
