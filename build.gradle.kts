plugins {
    kotlin("jvm") version "2.3.21"
    id("com.github.gmazzo.buildconfig") version "6.0.10"
    id("org.graalvm.buildtools.native") version "1.1.4"
    id("com.gradleup.shadow") version "9.3.2"
    application
}


application {
    mainClass.set("snkt.org.MainKt")
}

group = "snkt.org"
version = "1.0"


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.matching("GraalVM Community"))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Source: https://mvnrepository.com/artifact/com.unboundid/unboundid-ldapsdk
    implementation("com.unboundid:unboundid-ldapsdk:7.0.5")
    // Source: https://mvnrepository.com/artifact/com.sun.mail/jakarta.mail
    implementation("com.sun.mail:jakarta.mail:2.0.2")
    // Source: https://mvnrepository.com/artifact/io.github.oshai/kotlin-logging-jvm
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")
    // Source: https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    implementation("org.slf4j:slf4j-api:2.0.18")
    // Source: https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation("ch.qos.logback:logback-classic:1.5.38")
    // Source: https://mvnrepository.com/artifact/com.cronutils/cron-utils
    implementation("com.cronutils:cron-utils:9.2.1")
}

//kotlin {
//    jvmToolchain(25)
//}

buildConfig {
    val commandName: String? by rootProject.extra
    buildConfigField("String", "COMMAND_NAME", commandName)
}


graalvmNative {
    binaries {
        named("main") {
            imageName.set("passnotif")
            mainClass.set("snkt.org.MainKt")
            buildArgs.add("--no-fallback")
        }
    }
}