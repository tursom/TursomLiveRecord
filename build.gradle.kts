import com.google.protobuf.gradle.*
import cn.tursom.gradle.*

buildscript {
  repositories {
    maven {
      url = uri("https://nvm.tursom.cn/repository/maven-public/")
    }
  }
  dependencies {
    classpath("cn.tursom:ts-gradle:1.0-SNAPSHOT") { isChanging = true }
  }
  configurations {
    all {
      resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
      resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
  }
}

apply(plugin = "ts-gradle")

plugins {
  kotlin("jvm") version "1.6.10"
  id("com.google.protobuf") version "0.8.18"
  application
}

group = "cn.tursom"
version = "1.0"

repositories {
  maven {
    url = uri("https://nvm.tursom.cn/repository/maven-public/")
  }
  mavenCentral()
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
  `ts-async-http`
  `ts-pool`
  `ts-socket`
  `ts-coroutine`
  `ts-yaml`
  `ts-mail`
  `ts-ktorm`
  `ts-log`
  implementation(group = "cn.tursom", name = "tursom-im-sdk", version = tursomServerVersion)
  implementation(group = "cn.tursom", name = "BiliWS", version = tursomServerVersion)

  implementation(group = "org.ktorm", name = "ktorm-support-sqlite", version = "3.4.1")
  implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.36.0.3")
  implementation(group = "com.squareup.okhttp3", name = "okhttp", version = "4.9.3")
  implementation(group = "io.netty", name = "netty-tcnative-boringssl-static", version = "2.0.51.Final")
  //implementation(group = "io.netty", name = "netty-all", version = "4.1.65.Final")
  //implementation(group = "io.projectreactor.netty", name = "reactor-netty-http", version = "1.0.8")
  implementation(group = "xerces", name = "xercesImpl", version = "2.12.2")

  testImplementation(group = "junit", name = "junit", version = "4.13.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = "1.8"
  kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
  //kotlinOptions.useIR = true
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.20.1"
  }
  generatedFilesBaseDir = "$projectDir/src"
  //plugins {
  //  id("grpc") {
  //    artifact = "io.grpc:protoc-gen-grpc-java:1.38.0"
  //  }
  //}
  //generateProtoTasks {
  //  all().forEach {
  //    it.plugins {
  //      id("grpc") {
  //        outputSubDir = "java"
  //      }
  //    }
  //  }
  //}
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
