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

    // Модульные тесты чистой логики (без запуска IDE)
    testImplementation("junit:junit:4.13.2")
}

// Настройка IntelliJ Platform Plugin
intellij {
    intellijRepository.set("https://www.jetbrains.com/intellij-repository")
    val sdkPath = providers.gradleProperty("localIdePath").orNull
    if (sdkPath != null) localPath.set(sdkPath)
    else version.set(providers.gradleProperty("platformVersion"))
    type.set("IC") // IC = IntelliJ IDEA Community, IU = IntelliJ IDEA Ultimate
    
    plugins.set(listOf(
        // Сюда можно добавить плагины-зависимости
        // "com.intellij.java"
    ))
}

tasks {
    withType<org.jetbrains.intellij.tasks.InstrumentCodeTask>().configureEach {
        providers.gradleProperty("localIdePath").orNull?.let { sdk ->
            val compiler = file("$sdk/plugins/java/lib/javac2.jar")
            if (compiler.isFile) {
                compilerClassPathFromMaven.set(files(
                    fileTree("$sdk/lib") { include("*.jar") },
                    fileTree("$sdk/plugins/java/lib") { include("*.jar") }
                ).files.toList())
            }
        }
    }
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
        untilBuild.set("262.*")  // верхняя граница — WebStorm 2026.2, проверяемая перед релизом
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

