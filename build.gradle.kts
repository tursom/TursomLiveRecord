import com.google.protobuf.gradle.*

plugins {
  kotlin("jvm") version "1.5.20"
  id("com.google.protobuf") version "0.8.16"
  application
}

group = "cn.tursom"
version = "1.0"

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(group = "cn.tursom", name = "ts-pool", version = "0.2")
  implementation(group = "cn.tursom", name = "ts-socket", version = "0.2")
  implementation(group = "cn.tursom", name = "ts-coroutine", version = "0.2")
  implementation(group = "cn.tursom", name = "tursom-im-sdk", version = "1.0")
  implementation(group = "cn.tursom", name = "BiliWS", version = "1.0")

  implementation(group = "io.netty", name = "netty-tcnative-boringssl-static", version = "2.0.39.Final")
  //implementation(group = "io.netty", name = "netty-all", version = "4.1.65.Final")
  //implementation(group = "io.projectreactor.netty", name = "reactor-netty-http", version = "1.0.8")
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

application {
  applicationName = "DanmuMachine"
  mainClass.set("cn.tursom.record.DanmuMachineKt")
  applicationDefaultJvmArgs = listOf(
    "-Xmx32m",
    "-XX:MaxHeapFreeRatio=10",
    "-XX:MinHeapFreeRatio=10"
  )
}

application {
  applicationName = "LiveRecord"
  mainClass.set("cn.tursom.record.LiveKt")
  applicationDefaultJvmArgs = listOf(
    "-Xmx256m",
    "-XX:MaxHeapFreeRatio=10",
    "-XX:MinHeapFreeRatio=10"
  )
}

//application {
//  applicationName = "TursomLiveRecord"
//  mainClass.set("cn.tursom.record.MainKt")
//  applicationDefaultJvmArgs = listOf(
//    "-Xmx32m",
//    "-XX:MaxHeapFreeRatio=10",
//    "-XX:MinHeapFreeRatio=10"
//  )
//}
