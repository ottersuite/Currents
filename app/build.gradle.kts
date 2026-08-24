import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.baselineprofile)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "app.otter.client"
    compileSdk = 37

    defaultConfig {
        // Keep the shipped ID so the Otter rebrand updates Orca in place and retains app data.
        applicationId = "app.orca.client"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "REDDIT_CLIENT_ID",
            localProperties.getProperty("reddit.clientId", "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "REDDIT_USER_AGENT",
            localProperties.getProperty("reddit.userAgent", "").asBuildConfigString(),
        )
        buildConfigField("boolean", "BENCHMARK_MODE", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildTypes {
        getByName("release") {
            // Compose, Media3 (exoplayer + hls + dash), Coil, OkHttp and the extended icon set
            // ship a great deal this app never calls. R8 also rewrites the code the baseline
            // profile is generated against, so leaving it off would make the profile describe a
            // build that is never installed.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Inherits minification from release on purpose: a benchmark against an unminified
        // build measures something no user ever runs.
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "BENCHMARK_MODE", "true")
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        // The baseline-profile plugin collects against its own `nonMinified*` variants. Left
        // alone those launch the signed-out empty state, and the profile would describe a blank
        // screen rather than the feed. Give them the same demo repository the benchmark build
        // type uses, so the classes that actually cost time at startup are the ones recorded.
        if (variant.name.startsWith("nonMinified")) {
            variant.buildConfigFields?.put(
                "BENCHMARK_MODE",
                BuildConfigField("boolean", "true", "Demo content, so profiling reaches the feed"),
            )
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf"),
    )

    // ./gradlew.bat assembleRelease -PcomposeMetrics writes per-composable skippability and
    // per-class stability reports, which is the only way to check a stability change landed
    // rather than assuming it did.
    if (project.hasProperty("composeMetrics")) {
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    // Android stubs org.json in unit tests; the real implementation lets the media parser be
    // exercised against captured Reddit payloads on the JVM.
    testImplementation(libs.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    baselineProfile(project(":baselineprofile"))
}
