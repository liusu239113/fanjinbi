plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dshx.game.SU"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dshx.game.SU"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/fishingidle.jks")
            storePassword = "fishingidle"
            keyAlias = "fishingidle"
            keyPassword = "fishingidle"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
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
        compose = true
        // 打开 BuildConfig：设置页的版本号直接读 BuildConfig.VERSION_NAME，
        // 免得 gradle 里改了版本、界面上还硬编码着旧号。
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("ogg", "wav", "mp3")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        // 单元测试里 android.graphics.Color 等桩方法返回默认值而不是抛异常，
        // 这样纯逻辑的模拟测试可以直接跑在 JVM 上。
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ------------------------------------------------------------------
    // 广告 SDK（Tosin / TopOn 聚合）
    // core + adx + oaid + 各平台 adapter（穿山甲/优量汇/快手/百度/sigmob/topon）
    // 游戏逻辑不直接依赖它们：所有调用都收在 ads/ 包里，通过 RewardAds 暴露。
    // ------------------------------------------------------------------
    implementation(files("libs/tosin-ad-Y260817.aar"))
    implementation(files("libs/tosin-adx-2.9.65.aar"))
    implementation(files("libs/oaid_sdk_1.0.25.aar"))
    implementation(files("libs/tosin-csj-adapter-7.6.1.1.aar"))
    implementation(files("libs/tosin-gdt-adapter-4.690.1560.aar"))
    implementation(files("libs/tosin-ks-adapter-5.1.20.1.aar"))
    implementation(files("libs/tosin-baidu-adapter-9.450.aar"))
    implementation(files("libs/sigmob/tosin-sigmob_common-adapter-1.9.4.aar"))
    implementation(files("libs/sigmob/tosin-sigmob_windsdk-adapter-4.25.11.aar"))
    // ADX 业务的必需组件：缺了 SDK 每次启动都会弹「重要组件缺失」且初始化直接失败，
    // 表现就是所有「看广告」都提示没准备好。
    implementation(files("libs/topon/tosin-anythink_adx_sdk_kuying_necessary-adapter-6.5.48.aar"))
    implementation(files("libs/topon/tosin-anythink_network_adx_kuying_sdk_necessary-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_banner-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_china_core.aar"))
    implementation(files("libs/topon/tosin-anythink_core-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_interstitial-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_native-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_rewardvideo-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_splash-adapter.aar"))

    // TapTap 登录 + 防沉迷（compliance = 合规认证）
    implementation("com.taptap.sdk:tap-core:4.10.3")
    implementation("com.taptap.sdk:tap-login:4.10.3")
    implementation("com.taptap.sdk:tap-compliance:4.10.3")

    // 广告 SDK 的运行时依赖
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Tosin 的 TosinSDK 类内部用了 viewModelScope，直接引用 androidx.lifecycle.ViewModelKt。
    // 本工程是 Compose，虽然带进来一部分 lifecycle，但显式声明更稳妥：
    // 缺了它 TosinSDK 一加载就抛 NoClassDefFoundError，广告初始化直接失败。
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.2.0")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")

    testImplementation("junit:junit:4.13.2")
}
