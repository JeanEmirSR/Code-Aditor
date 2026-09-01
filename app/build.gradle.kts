plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jeansr.androideditor"
    compileSdk = 36
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            pickFirst("**/*.kotlin_builtins")
            pickFirst("META-INF/*.kotlin_module")
        }
    }

    defaultConfig {
        applicationId = "com.jeansr.androideditor"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")
    implementation("io.github.reandroid:ARSCLib:1.3.8")
    implementation ("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("com.android.tools:r8:8.13.19")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.0")
}