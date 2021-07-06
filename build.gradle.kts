import com.google.protobuf.gradle.*

plugins {
  kotlin("jvm") version "1.5.20"
  id("com.google.protobuf") version "0.8.16"
}

group = "cn.tursom"
version = "1.0-SNAPSHOT"

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(group = "cn.tursom", name = "tursom-im-sdk", version = "1.0")
  implementation(group = "cn.tursom", name = "BiliWS", version = "1.0")
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
