import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("java-test-fixtures")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.changelog") version "2.5.0"
    id("com.diffplug.spotless") version "8.1.0"
    id("pmd")
}

val ideaVimPluginVersion="2.27.2"
val goPluginVersion="252.27397.103"
val pythonCorePluginVersion="252.28238.7"
val pythonidPluginVersion="252.28238.7"
val rubyPluginVersion="252.28238.7"
val rustPluginVersion="252.28238.28"
val phpPluginVersion="252.28238.9"
val scalaPluginVersion="2025.2.48"
val perl5PluginVersion="2025.2.1"
val r4intellijPluginVersion="252.28238.33"
val notebooksPluginVersion="253.28294.332"
val dartPluginVersion="500.0.0"

kotlin {
    jvmToolchain(21)
}

changelog {
    version.set(providers.gradleProperty("pluginVersion"))
    path.set(file("CHANGELOG.md").canonicalPath)
    header.set(provider { "[${version.get()}]" })
    headerParserRegex.set("""(\d+\.\d+\.\d+)""".toRegex())
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Next]")
    groups.set(listOf(""))
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val platformVersion = providers.gradleProperty("platformVersion")

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), platformVersion)
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Starter)
    }

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // JUnit 4
    testImplementation("junit:junit:4.13.2")
    // Dependency Injection for Starter configuration
    testImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
}

val trustAllProjectsArg = CommandLineArgumentProvider {
    listOf("-Didea.trust.all.projects=true")
}

// To test C# manually
val runRiderWithPlugins by intellijPlatformTesting.runIde.registering {
    type = IntelliJPlatformType.Rider
    task {
        jvmArgumentProviders += trustAllProjectsArg
    }
}

// To test C and C++ manually
// Note: Somehow debugging doesn't work with CLion...
val runClionWithPlugins by intellijPlatformTesting.runIde.registering {
    type = IntelliJPlatformType.CLion
    version = platformVersion
    task {
        jvmArgumentProviders += trustAllProjectsArg
    }
}

// To test all other languages manually
val runIdeaUltimateWithPlugins by intellijPlatformTesting.runIde.registering {
    type = IntelliJPlatformType.IntellijIdeaUltimate
    version = platformVersion
    plugins {
        plugin("org.jetbrains.plugins.go", goPluginVersion)
        plugin("PythonCore", pythonCorePluginVersion)
        plugin("Pythonid", pythonidPluginVersion)
        plugin("org.jetbrains.plugins.ruby", rubyPluginVersion)
        plugin("com.jetbrains.rust", rustPluginVersion)
        plugin("com.jetbrains.php", phpPluginVersion)
        plugin("org.intellij.scala", scalaPluginVersion)
        plugin("com.perl5", perl5PluginVersion)
        plugin("R4Intellij", r4intellijPluginVersion)
        plugin("com.intellij.notebooks.core", notebooksPluginVersion)
        plugin("Dart", dartPluginVersion)
    }
    task {
        jvmArgumentProviders += trustAllProjectsArg
    }
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginId")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

spotless {
    java {
        removeUnusedImports()
        googleJavaFormat("1.22.0")
            .aosp()
            .reflowLongStrings()
            .groupArtifact("com.google.googlejavaformat:google-java-format")
    }
    kotlin {
        target("src/**/*.kt")
        ktlint()
    }
}

pmd {
    isConsoleOutput = true
    toolVersion = "6.46.0"
    rulesMinimumPriority.set(5)
    ruleSetFiles = rootProject.files("pmd-config.xml")
    ruleSets = emptyList()
    isIgnoreFailures = false
}

tasks.named<Pmd>("pmdMain") {
    reports {
        html.required.set(true)
    }
}

tasks {
    val buildPlugin by existing(Zip::class)

    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    val setupTestProject by registering(Copy::class) {
        from(layout.projectDirectory.dir("src/test/testFiles"))
        into(layout.projectDirectory.dir("src/test/tmpFiles"))
    }

    withType<Test> {
        useJUnitPlatform()
        dependsOn(setupTestProject)
        dependsOn(buildPlugin)
        systemProperty("path.to.build.plugin", buildPlugin.get().archiveFile.get().asFile.absolutePath)
        systemProperty("platform.version", platformVersion.get())
        systemProperty("path.to.project", layout.projectDirectory.dir("src/test/tmpFiles").asFile.absolutePath)
        systemProperty("ideaVimPluginVersion", ideaVimPluginVersion)
        systemProperty(
            "intellijUltimatePlugins",
            listOf(
                "org.jetbrains.plugins.go:$goPluginVersion",
                "PythonCore:$pythonCorePluginVersion",
                "Pythonid:$pythonidPluginVersion",
                "org.jetbrains.plugins.ruby:$rubyPluginVersion",
                "com.jetbrains.rust:$rustPluginVersion",
                "com.jetbrains.php:$phpPluginVersion",
                "org.intellij.scala:$scalaPluginVersion",
                "com.perl5:$perl5PluginVersion",
                "R4Intellij:$r4intellijPluginVersion",
                "com.intellij.notebooks.core:$notebooksPluginVersion",
                "Dart:$dartPluginVersion",
            ).joinToString(","),
        )
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
        dependsOn(patchChangelog)
    }

    buildSearchableOptions {
        enabled = false
    }
}