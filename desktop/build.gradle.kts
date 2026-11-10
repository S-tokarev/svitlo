import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
    sourceSets.named("main") {
        kotlin.setSrcDirs(listOf("src/main/kotlin", "../app/src/main/java"))
        kotlin.exclude("**/MainActivity.kt")
        kotlin.exclude("**/SvitloApp.kt")
        kotlin.exclude("**/alert/**")
        kotlin.exclude("**/ui/**")
        kotlin.exclude("**/widget/**")
        kotlin.exclude("**/SvitloRepository.kt")
        kotlin.exclude("**/SvitloStore.kt")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "ua.in.vyshgorod.svitlo.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Svitlo"
            packageVersion = "1.1.0"
            description = "Svitlo"
            vendor = "Svitlo"
            macOS {
                bundleID = "ua.in.vyshgorod.svitlo.desktop"
            }
            windows {
                menu = true
                shortcut = true
            }
        }
    }
}
