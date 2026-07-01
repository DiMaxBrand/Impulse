import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.1"
    id("com.diffplug.spotless") version "8.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

val abiCodes = mapOf("armeabi-v7a" to 1, "x86" to 2, "x86_64" to 3, "arm64-v8a" to 4)

val signingProps = Properties()
val signingPropsFile = rootProject.file("signing.properties")
if (signingPropsFile.exists()) signingProps.load(signingPropsFile.inputStream())

spotless {
    ratchetFrom("2.21.0")
    java {
        target("**/*.java")
        googleJavaFormat().aosp().reflowLongStrings()
    }
}

// ---- Release version — edit here ----
val baseVersionCode = 42240
val appVersion = "1.11.0-rc.5+2.20.0"

@Suppress("DEPRECATION")
android {
    namespace = "eu.siacs.conversations"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
        targetSdk = 36
        versionCode = baseVersionCode
        versionName = appVersion
        applicationId = "com.dimax.impulse"
        resValue("string", "applicationId", applicationId!!)
        val appName = "Impulse"
        buildConfigField("String", "APP_NAME", "\"$appName\"")
        base {
            archivesName.set("Impulse")
        }
    }

    splits {
        abi {
            isUniversalApk = true
            isEnable = true
            reset()
            //noinspection ChromeOsAbiSupport
            include(*abiCodes.keys.toTypedArray())
        }
    }

    configurations {
        implementation {
            exclude(group = "org.jetbrains", module = "annotations")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    flavorDimensions += "mode"
    flavorDimensions += "distribution"

    productFlavors {
        create("conversations") {
            dimension = "mode"
            buildConfigField("String", "PRIVACY_POLICY", "\"https://conversations.im/privacy.html\"")
        }
        create("free") {
            dimension = "distribution"
            versionNameSuffix = "+free"
        }
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    if (signingProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(signingProps["keystore"] as String)
                storePassword = signingProps["keystore.password"] as String
                keyAlias = signingProps["keystore.alias"] as String
                keyPassword = signingProps["keystore.password"] as String
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    packaging {
        resources {
            excludes += "META-INF/BCKEY.DSA"
            excludes += "META-INF/BCKEY.SF"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    lint {
        disable += "MissingTranslation"
        disable += "InvalidPackage"
        disable += "AppCompatResource"
    }

    buildFeatures {
        buildConfig = true
        resValues = true
        dataBinding = true
        compose = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        )
    }
}

afterEvaluate {
    @Suppress("DEPRECATION")
    android.sourceSets.getByName("conversationsFree") {
        java.setSrcDirs(listOf("src/conversationsFree/java"))
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiFilter = output.filters
                .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier
            val abiCode = abiFilter?.let { abiCodes[it] }
            output.versionCode.set(100 * baseVersionCode + (abiCode ?: 0))

            if (variant.buildType == "release") {
                val abiTag = when (abiFilter) {
                    "arm64-v8a"   -> "arm64"
                    "armeabi-v7a" -> "arm32"
                    "x86_64"      -> "x64"
                    "x86"         -> "x86"
                    else          -> "universal"
                }
                output.outputFileName.set("Impulse_$abiTag.apk")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    // Override BOM's stable material3:1.4.0 with the latest alpha to get full M3 Expressive APIs
    implementation("androidx.compose.material3:material3:1.5.0-alpha21")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.activity:activity-compose:1.10.1")

    annotationProcessor("org.immutables:value:2.12.1")
    implementation("org.immutables:value-annotations:2.12.1")
    annotationProcessor(project(":libs:annotation-processor"))
    implementation(project(":libs:annotation"))
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.emoji2:emoji2:1.6.0")
    implementation("androidx.emoji2:emoji2-emojipicker:1.6.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.heifwriter:heifwriter:1.1.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.sharetarget:sharetarget:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.viewpager:viewpager:1.1.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.github.open-keychain.open-keychain:openpgp-api:v5.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.guava:guava:33.6.0-android")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta5")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.leinardi.android:speed-dial:3.3.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.vanniktech:android-image-cropper:4.7.0")
    implementation("im.conversations.webrtc:webrtc-android:129.0.0")
    implementation("io.deepmedia.community:transcoder-android:0.11.2")
    implementation("me.leolin:ShortcutBadger:1.1.22@aar")
    //noinspection NewerVersionAvailable
    implementation("net.fellbaum:jemoji:1.7.6") //1.7.6 is the latest with Java 8 support
    implementation("org.bouncycastle:bcmail-jdk18on:1.84")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.hsluv:hsluv:1.0")
    implementation("org.jxmpp:jxmpp-jid:1.1.0")
    implementation("org.jxmpp:jxmpp-stringprep-libidn:1.1.0")
    implementation("org.minidns:minidns-client:1.1.1")
    implementation("org.minidns:minidns-dnssec:1.1.1")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("org.whispersystems:signal-protocol-java:2.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
