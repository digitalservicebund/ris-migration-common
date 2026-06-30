plugins {
  `java-library`
  id("io.spring.dependency-management") version "1.1.7"
  id("com.diffplug.spotless") version "7.0.4"
  id("jacoco")
  `maven-publish`
}

repositories {
  mavenCentral()
}

group = "de.bund.digitalservice.ris"
version = "1.0.0"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

val awsVersion = "2.46.15"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
  }
}

dependencies {
  api("org.springframework.boot:spring-boot-autoconfigure")
  api("org.springframework.boot:spring-boot-starter-batch")
  api("org.springframework.boot:spring-boot-starter-batch-jdbc")
  api("org.springframework.boot:spring-boot-starter-data-jpa")
  api(platform("software.amazon.awssdk:bom:$awsVersion"))
  api("software.amazon.awssdk:s3")
  api("software.amazon.awssdk:s3-transfer-manager")
  api("com.fasterxml.jackson.core:jackson-databind")
  api("org.apache.commons:commons-lang3")
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
  testCompileOnly("org.projectlombok:lombok")
  testAnnotationProcessor("org.projectlombok:lombok")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
  useJUnitPlatform()
}

spotless {
  java {
    eclipse()
    target("src/*/java/**/*.java")
  }
}

jacoco {
  toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
  executionData.setFrom(
    files(fileTree(project.layout.buildDirectory) { include("jacoco/*.exec") }),
  )
  reports {
    xml.required = true
    html.required = true
  }
  dependsOn("test")
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
    }
  }
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/digitalservicebund/ris-migration-common")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}
