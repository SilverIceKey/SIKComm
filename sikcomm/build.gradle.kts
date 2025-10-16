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
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
                groupId = project.findProperty("GROUP_ID") as String
                artifactId = "SIKComm"
                version = project.findProperty("VERSION") as String
            }

            pom {
                name.set("SIKComm")
                description.set("Ble蓝牙+modbus的工具库")
                url.set("https://github.com/SilverIceKey/SIKComm")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("zheqian")
                        name.set("折千")
                        email.set("z516798599@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/SilverIceKey/SIKComm.git")
                    developerConnection.set("scm:git:ssh://github.com/SilverIceKey/SIKComm.git")
                    url.set("https://github.com/SilverIceKey/SIKComm")
                }
            }
        }
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
}

