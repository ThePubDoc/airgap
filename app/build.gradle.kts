import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing settings, kept outside the repo. Defaults to
 * ~/.airgap-signing/keystore.properties; override with the `airgap.signingProperties`
 * Gradle property or the AIRGAP_SIGNING_PROPERTIES environment variable.
 * When the file is missing, release builds come out unsigned.
 */
val releaseSigning: Properties? = providers.gradleProperty("airgap.signingProperties")
    .orElse(providers.environmentVariable("AIRGAP_SIGNING_PROPERTIES"))
    .orElse(System.getProperty("user.home") + "/.airgap-signing/keystore.properties")
    .map { file(it) }
    .get()
    .takeIf { it.isFile }
    ?.let { propertiesFile -> Properties().apply { propertiesFile.inputStream().use { load(it) } } }

android {
    namespace = "app.airgap"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.airgap"
        // API 30 is the first level where a Keystore key can be unlocked by
        // either biometrics or the device PIN through one BiometricPrompt.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release app, so testing never touches the real vault.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Errors fail the build; warnings are reported but don't.
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose's test library brings Espresso 3.5, which fails on Android 15+ (removed InputManager.getInstance).
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}

/**
 * Fails the build if any network permission reaches the merged manifest,
 * for example because a new dependency declares one. The app must never
 * be able to reach the network.
 */
abstract class VerifyNoNetworkPermission : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val forbidden = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
        )
        val found = forbidden.filter { permission ->
            Regex("""<uses-permission[^>]*android:name="${Regex.escape(permission)}"""").containsMatchIn(manifest)
        }
        if (found.isNotEmpty()) {
            throw GradleException("Network permissions found in merged manifest: $found")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoNetworkPermission>("verify${variantName}NoNetworkPermission") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble$variantName" || it.name == "bundle$variantName" }
            .configureEach { dependsOn(verify) }
    }
}
