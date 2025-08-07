plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.sik.comm"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

publishing {
    publications {

        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
                groupId = project.findProperty("GROUP_ID") as String // 使用 GROUP_ID 属性
                artifactId = "SIKCamera"
                version = project.findProperty("VERSION") as String // 使用 VERSION_NAME 属性
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}

