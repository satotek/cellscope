import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// One version for the APK and the priv-app module; a release tag must match it.
val appVersion = providers.gradleProperty("cellscope.versionName").get()
val appVersionCode = appVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "versionName $appVersion does not fit the versionCode scheme" }
    major * 10_000 + minor * 100 + patch
}

// CI passes the release key through the environment; a workstation keeps it in a properties
// file outside the repository. Without either, builds fall back to the per-machine debug key.
val releaseSigning: Map<String, String>? = run {
    val env = System.getenv()
    if (env["CELLSCOPE_KEYSTORE"] != null) return@run mapOf(
        "storeFile" to env.getValue("CELLSCOPE_KEYSTORE"),
        "storePassword" to env.getValue("CELLSCOPE_KEYSTORE_PASSWORD"),
        "keyAlias" to env.getValue("CELLSCOPE_KEY_ALIAS"),
        "keyPassword" to env.getValue("CELLSCOPE_KEY_PASSWORD"),
    )
    val file = providers.gradleProperty("cellscope.signing").orNull?.let(::File)
        ?: File(System.getProperty("user.home"), ".config/cellscope/keystore.properties")
    if (!file.isFile) return@run null
    Properties().apply { file.inputStream().use(::load) }.let { p -> p.stringPropertyNames().associateWith(p::getProperty) }
}
if (releaseSigning == null && providers.gradleProperty("cellscope.requireReleaseSigning").isPresent) {
    throw GradleException("Release signing requested but no key is configured")
}

android {
    namespace = "dev.satotek.cellscope"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "dev.satotek.cellscope"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        releaseSigning?.let { k ->
            create("release") {
                storeFile = file(k.getValue("storeFile"))
                storePassword = k.getValue("storePassword")
                keyAlias = k.getValue("keyAlias")
                keyPassword = k.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug builds are a separate, debug-key app: a debuggable APK under the release key could
        // replace a release install and expose its data through run-as.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 drops the unused ~40 MB of material-icons-extended; nothing we reflect on lives in our own code.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.okhttp)
}
