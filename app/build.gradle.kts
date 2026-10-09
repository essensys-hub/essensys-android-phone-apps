plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.essensys.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.essensys.android"
        minSdk = 26
        targetSdk = 36
        // Fournis par release.yml depuis le tag android-vX.Y.Z ; valeurs locales par défaut.
        versionCode = System.getenv("ESSENSYS_VERSION_CODE")?.toInt() ?: 2
        versionName = System.getenv("ESSENSYS_VERSION_NAME") ?: "2.0.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signature release (design D9) : keystore hors dépôt (SOPS essensys-ansible/secrets/cloud/android-release.sops.yaml),
    // transmis par variables d'environnement. Jamais de keystore ni de mot de passe dans le dépôt.
    signingConfigs {
        create("release") {
            System.getenv("ESSENSYS_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = System.getenv("ESSENSYS_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ESSENSYS_KEY_ALIAS")
            keyPassword = System.getenv("ESSENSYS_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Réseau
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Échec explicite si une release est demandée sans les variables de signature.
val releaseSigningEnv = listOf("ESSENSYS_KEYSTORE_PATH", "ESSENSYS_KEYSTORE_PASSWORD", "ESSENSYS_KEY_ALIAS", "ESSENSYS_KEY_PASSWORD")
gradle.taskGraph.whenReady {
    if (allTasks.any { it.path.startsWith(":app:") && it.name.contains("Release") && (it.name.startsWith("package") || it.name.startsWith("assemble") || it.name.startsWith("bundle")) }) {
        val missing = releaseSigningEnv.filter { System.getenv(it).isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Signature release impossible : variables manquantes ${missing.joinToString()} " +
                    "(keystore : essensys-ansible/secrets/cloud/android-release.sops.yaml, voir README).",
            )
        }
    }
}

