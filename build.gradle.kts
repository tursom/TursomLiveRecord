plugins {
  kotlin("jvm") version "1.5.20"
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
}
