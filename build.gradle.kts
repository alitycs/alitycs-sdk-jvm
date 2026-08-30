import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
    `maven-publish`
}

group = "com.alitycs"
version = "1.1.2"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenCentral()
}

val conformanceSourceDirectory = file("../conformance/apps/jvm")

kotlin.sourceSets.named("test") {
    if (conformanceSourceDirectory.isDirectory) {
        kotlin.srcDir(conformanceSourceDirectory)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

tasks.test {
    useJUnitPlatform()
    workingDir(layout.projectDirectory)
    systemProperty("alitycs.repositoryRoot", ".")
    inputs.files(
        ".coderabbit.yaml",
        "build.gradle.kts",
        "CONTRIBUTING.md",
        "README.md",
        fileTree(".github") {
            include("**/*.yml", "**/*.yaml", "PULL_REQUEST_TEMPLATE.md", "CODEOWNERS")
        },
        fileTree("docs") { include("**/*.md") },
        fileTree("scripts") { include("**/*") },
        fileTree(".") {
            include("**/action.yml", "**/action.yaml", "**/Dockerfile")
            exclude(".git/**", ".gradle/**", "**/build/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<JavaExec>("runConformance") {
    group = "verification"
    description = "Runs the JVM SDK against the shared conformance capture server."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.alitycs.sdk.conformance.ConformanceMainKt")
    doFirst {
        check(conformanceSourceDirectory.isDirectory) {
            "Shared conformance sources are missing. Clone the Alitycs SDK workspace so " +
                "../conformance/apps/jvm exists."
        }
    }
}

tasks.register<JavaExec>("runE2e") {
    group = "verification"
    description = "Runs the JVM SDK against a full Alitycs stack for the cross-service SDK suite."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.alitycs.sdk.e2e.E2eMainKt")
}

kover {
    reports {
        verify {
            rule("Minimum line coverage") {
                minBound(90)
            }
        }
    }
}

val koverXmlReportFile = layout.buildDirectory.file("reports/kover/report.xml")
val verifyFunctionCoverage by tasks.registering {
    group = "verification"
    description = "Verifies JVM method coverage as the SDK function-coverage gate."
    dependsOn(tasks.named("koverXmlReport"))
    inputs.file(koverXmlReportFile)

    doLast {
        val report = javax.xml.parsers.DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(koverXmlReportFile.get().asFile)
        val counters = report.documentElement.getElementsByTagName("counter")
        var covered = -1
        var missed = -1

        for (index in 0 until counters.length) {
            val counter = counters.item(index) as org.w3c.dom.Element
            if (counter.parentNode == report.documentElement && counter.getAttribute("type") == "METHOD") {
                covered = counter.getAttribute("covered").toInt()
                missed = counter.getAttribute("missed").toInt()
                break
            }
        }

        check(covered >= 0 && missed >= 0) { "Kover XML report has no aggregate METHOD counter" }
        val percentage = covered.toDouble() / (covered + missed) * 100
        logger.lifecycle("application function coverage: %.4f%%".format(percentage))
        check(percentage >= 85.0) {
            "Function coverage %.4f%% is below the required 85.0%%".format(percentage)
        }
    }
}

tasks.named("koverVerify") {
    dependsOn(verifyFunctionCoverage)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Alitycs JVM SDK")
                description.set("Kotlin-first JVM SDK for sending product-analytics events to Alitycs")
                url.set("https://github.com/alitycs/alitycs-sdk-jvm")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("alitycs")
                        name.set("Alitycs Team")
                        organization.set("Alitycs")
                        organizationUrl.set("https://github.com/alitycs")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/alitycs/alitycs-sdk-jvm.git")
                    developerConnection.set("scm:git:ssh://git@github.com/alitycs/alitycs-sdk-jvm.git")
                    url.set("https://github.com/alitycs/alitycs-sdk-jvm")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/alitycs/alitycs-sdk-jvm/issues")
                }
            }
        }
    }
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "com.alitycs.sdk"
        attributes["Implementation-Title"] = "Alitycs JVM SDK"
        attributes["Implementation-Version"] = project.version
    }
    from("LICENSE") {
        into("META-INF")
    }
}

tasks.named<Jar>("javadocJar") {
    from("README.md")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register("verifyReleaseVersion") {
    group = "verification"
    description = "Checks that -PreleaseTag=vX.Y.Z matches the project version."
    doLast {
        val releaseTag = providers.gradleProperty("releaseTag").orNull
        check(releaseTag != null && releaseTag.matches(Regex("^v\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?$"))) {
            "-PreleaseTag must be a semantic version tag such as v1.0.0"
        }
        check(releaseTag == "v${project.version}") {
            "Release tag $releaseTag does not match project version ${project.version}"
        }
        logger.lifecycle("Release tag $releaseTag matches project version ${project.version}.")
    }
}
