import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("java-test-fixtures")
    kotlin("jvm") version "1.9.23"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.changelog") version "2.2.1"
    id("com.diffplug.spotless") version "6.25.0"
    id("pmd")
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

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        instrumentationTools()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

// To test C#
val runRiderWithPlugins by intellijPlatformTesting.runIde.registering {
    type = IntelliJPlatformType.Rider
    version = providers.gradleProperty("platformVersion")
}

val runIdeaUltimateWithPlugins by intellijPlatformTesting.runIde.registering {
    type = IntelliJPlatformType.IntellijIdeaUltimate
    version = providers.gradleProperty("platformVersion")
    plugins {
        plugin("org.jetbrains.plugins.go", "243.26053.27")
        plugin("PythonCore", "243.24978.46")
        plugin("Pythonid", "243.26053.27")
        plugin("org.jetbrains.plugins.ruby", "243.26053.27")
        plugin("com.jetbrains.rust", "243.26053.17")
        plugin("com.jetbrains.php", "243.26053.27")
        plugin("org.intellij.scala", "2024.3.42")
        plugin("com.perl5", "2024.3.1")
        plugin("R4Intellij", "243.26053.34")
        plugin("com.intellij.notebooks.core", "243.24978.74")
        plugin("Dart", "243.26053.17")
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
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
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