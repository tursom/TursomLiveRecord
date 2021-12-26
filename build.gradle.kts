import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "1.6.10"
    id("com.google.protobuf") version "0.8.16"
    application
}

group = "cn.tursom"
version = "1.0"

repositories {
    // mavenLocal()
    // mavenCentral()
    maven {
        url = uri("https://nvm.tursom.cn/repository/maven-public/")
        // url = uri("https://maven.pkg.github.com/tursom/TursomServer")
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    all {
        resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
        resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    val tursomServerVersion = "1.0-SNAPSHOT"
    implementation(group = "cn.tursom", name = "ts-async-http", version = tursomServerVersion)
    implementation(group = "cn.tursom", name = "ts-pool", version = tursomServerVersion)
    implementation(group = "cn.tursom", name = "ts-socket", version = tursomServerVersion)
    implementation(group = "cn.tursom", name = "ts-coroutine", version = tursomServerVersion)
    implementation(group = "cn.tursom", name = "tursom-im-sdk", version = tursomServerVersion)
    implementation(group = "cn.tursom", name = "BiliWS", version = tursomServerVersion)

    implementation(group = "com.squareup.okhttp3", name = "okhttp", version = "4.9.3")
    implementation(group = "io.netty", name = "netty-tcnative-boringssl-static", version = "2.0.46.Final")
    //implementation(group = "io.netty", name = "netty-all", version = "4.1.65.Final")
    //implementation(group = "io.projectreactor.netty", name = "reactor-netty-http", version = "1.0.8")

    testImplementation(group = "junit", name = "junit", version = "4.12")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    //kotlinOptions.useIR = true
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.17.1"
    }
    generatedFilesBaseDir = "$projectDir/src"
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.38.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc") {
                    outputSubDir = "java"
                }
            }
        }
    }
}

// skip test
if (project.gradle.startParameter.taskNames.firstOrNull { taskName ->
        ":test" in taskName
    } == null) {
    tasks {
        test { enabled = false }
        testClasses { enabled = false }
        compileTestJava { enabled = false }
        compileTestKotlin { enabled = false }
        processTestResources { enabled = false }
    }
}

application {
    applicationName = "DanmuMachine"
    mainClass.set("cn.tursom.record.DanmuMachineKt")
    applicationDefaultJvmArgs = listOf(
        "-Xmx32m",
        "-XX:MaxHeapFreeRatio=10",
        "-XX:MinHeapFreeRatio=10"
    )
}

// application {
//     applicationName = "LiveRecord"
//     mainClass.set("cn.tursom.record.LiveKt")
//     applicationDefaultJvmArgs = listOf(
//         "-Xmx256m",
//         "-XX:MaxHeapFreeRatio=10",
//         "-XX:MinHeapFreeRatio=10"
//     )
// }

//application {
//  applicationName = "TursomLiveRecord"
//  mainClass.set("cn.tursom.record.MainKt")
//  applicationDefaultJvmArgs = listOf(
//    "-Xmx32m",
//    "-XX:MaxHeapFreeRatio=10",
//    "-XX:MinHeapFreeRatio=10"
//  )
//}

// dependencyManagement {
//   resolutionStrategy {
//     cacheChangingModulesFor(0, TimeUnit.SECONDS)
//   }
// }
