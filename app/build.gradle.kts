plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ansimdrive.pleos"
    compileSdk = 35

    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.ansimdrive.pleos"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // 에뮬레이터 위치가 안 바뀔 때 assets/demo_route.json 재생. ./gradlew assembleDebug -PdemoMode=true
        // 컴파일 타임 상수면 증분 빌드에서 이전 값이 남으므로 parseBoolean으로 감싼다.
        val demoMode = (project.findProperty("demoMode") ?: "false").toString()
        buildConfigField("boolean", "DEMO_MODE", "Boolean.parseBoolean(\"$demoMode\")")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 음성 인식 엔진: vosk(기기 내 인식, 에뮬레이터용) / system(Android SpeechRecognizer, 실차용). -PsttEngine=...
    // 문자열 상수는 증분 빌드에서 인라인된 옛 값이 남으므로 String.valueOf로 감싼다.
    val sttEngine = project.findProperty("sttEngine")?.toString()
    buildTypes {
        debug {
            buildConfigField("String", "STT_ENGINE", "String.valueOf(\"${sttEngine ?: "vosk"}\")")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "STT_ENGINE", "String.valueOf(\"${sttEngine ?: "system"}\")")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    // 기기 내 음성 인식 (에뮬레이터용). 모델은 assets/vosk-model-ko (저장소 미포함, docs/voice-lines.md)
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    testImplementation("junit:junit:4.13.2")
}
