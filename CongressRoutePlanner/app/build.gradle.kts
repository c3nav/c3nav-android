plugins {
    id("com.android.application")
}

val versionMajor = 4
val versionMinor = 2
val versionPatch = 5
val minimumSdkVersion = 14

android {
    namespace = "de.c3nav.droid"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "de.c3nav.droid"
        minSdk = minimumSdkVersion
        targetSdk = 34
        versionCode = generateVersionCode()
        versionName = generateVersionName()
        buildConfigField("String", "WEB_URL", "\"https://37c3.c3nav.de\"")
    }
    signingConfigs {
        create("release") {
            storeFile = file("../../c3nav.keystore")
            storePassword = System.getenv("KSTOREPWD")
            keyAlias = "c3nav"
            keyPassword = System.getenv("KSTOREPWD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        disable.add("AddJavascriptInterface")
        disable.add("SetJavaScriptEnabled")
    }
}

fun generateVersionCode(): Int {
    return minimumSdkVersion * 1000000 + versionMajor * 10000 + versionMinor * 100 + versionPatch
}

fun generateVersionName(): String {
    return "${versionMajor}.${versionMinor}.${versionPatch}"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.google.android.material:material:1.0.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.preference:preference:1.1.0")
    implementation("androidx.legacy:legacy-preference-v14:1.0.0")
    implementation("com.android.support:support-annotations:28.0.0")
    testImplementation("junit:junit:4.12")
}
