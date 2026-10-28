plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.github.chinese-annotations"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}

// Настройка IntelliJ Platform Plugin
intellij {
    version.set("2023.1.5")
    type.set("IC") // IC = IntelliJ IDEA Community, IU = IntelliJ IDEA Ultimate
    
    plugins.set(listOf(
        // Сюда можно добавить плагины-зависимости
        // "com.intellij.java"
    ))
}

tasks {
    // Совместимость JVM
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    // Ускорение задачи runIde
    runIde {
        jvmArgs = listOf(
            "-Xmx3072m",
            "-XX:MaxMetaspaceSize=512m",
            "-XX:+UseG1GC",
            "-XX:+UseStringDeduplication",
            "-Didea.ProcessCanceledException=disabled",
            "-Didea.debug.mode=true",
            "-Didea.system.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-system",
            "-Didea.config.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-config",
            "-Didea.plugins.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-plugins",
            "-Didea.log.path=${System.getProperty("java.io.tmpdir")}/idea-sandbox-logs"
        )
    }

    // Ускорение сборки
    buildSearchableOptions {
        enabled = false  // сборка поисковых опций отключена ради скорости
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("999.*")  // без верхней границы: как в опубликованной 2.7.0, ставится в IDE 2026.x
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

