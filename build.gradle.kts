import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.github.viewbindingidfinder"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Target platform. Android Studio is built on the IntelliJ Platform; building against
// IntelliJ IDEA Community with the Android/Kotlin plugins gives us the Kotlin + UAST APIs
// we need and keeps the plugin compatible with Android Studio as well.
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.5")

        // Kotlin plugin: required for UAST resolution of Kotlin code.
        bundledPlugin("org.jetbrains.kotlin")
        // Java support (PSI/UAST for Java usages).
        bundledPlugin("com.intellij.java")

        pluginVerifier()
        zipSigner()

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.viewbindingidfinder"
        name = "ViewBinding Id Finder"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }

        description = """
            From an id in an Android XML layout, jump only to the code usages that access
            that view through the layout's generated ViewBinding class
            (e.g. <code>ActivityMainBinding.textTitle</code>), instead of every usage of the
            raw id across the whole project. Read and write (set) usages are shown with
            distinct gutter icons.
        """.trimIndent()

        vendor {
            name = "Mehmet Kurtgoz"
            email = "kurtgozmehmet159@gmail.com"
            url = "https://github.com/iamkurtgoz/ViewBindingIdFinder"
        }

        changeNotes = "Detect usages through abstract/generic ViewBinding base classes; de-duplicate results; add a Find ViewBinding Parents action on layout files."
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            // Verify only within the supported range (242 .. 252.*); avoids false
            // positives from far-future EAP builds that restructure XML modules.
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                sinceBuild = "251"
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    wrapper {
        gradleVersion = "9.5.0"
    }
}
