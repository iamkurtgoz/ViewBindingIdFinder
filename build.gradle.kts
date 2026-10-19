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
            <p>Connects your Android layout XML to the code that uses it <b>through
            ViewBinding</b> &mdash; precisely, not by matching raw ids across the whole
            project.</p>

            <h3>Features</h3>
            <ul>
              <li><b>Jump from an <code>android:id</code> to its ViewBinding usages.</b>
                  Click the gutter icon next to any <code>android:id</code> in a layout and
                  get only the places in code that access that view through the layout's
                  generated binding class (e.g. <code>activity_main.xml</code> &rarr;
                  <code>ActivityMainBinding</code>, <code>@+id/text_title</code> &rarr;
                  <code>ActivityMainBinding.textTitle</code>).</li>
              <li><b>Works with abstract / generic binding wrappers.</b> Detects usages even
                  when the binding is exposed through a base class such as
                  <code>abstract class ViewBindingActivity&lt;VB : ViewBinding&gt;</code> or a
                  delegated <code>by viewBinding(...)</code> &mdash; not just direct
                  <code>ActivityMainBinding</code> fields.</li>
              <li><b>Find ViewBinding Parents.</b> Right-click a layout file (in the editor or
                  the Project view) &rarr; <b>Find ViewBinding Parents</b> to jump to the
                  class(es) that use that layout's binding. Opens directly if there is one,
                  shows a chooser if there are several.</li>
              <li><b>Kotlin and Java</b> are both supported.</li>
            </ul>

            <h3>How to use</h3>
            <ul>
              <li><b>From a layout id:</b> open a layout under <code>res/layout*/</code>, find
                  the gutter icon next to an <code>android:id</code>, and click it. One usage
                  navigates straight to it; multiple usages show a popup listing them as
                  <code>File:line</code>.</li>
              <li><b>From a layout file:</b> right-click the <code>.xml</code> file (editor tab
                  or Project view) &rarr; <b>Find ViewBinding Parents</b> to go to the
                  screen(s) that use its binding.</li>
            </ul>

            <p>Detection is static; reflection-based or fully indirect access is not
            detected.</p>
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
