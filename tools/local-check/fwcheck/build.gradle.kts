// Type-checks the NON-UI app code against the real Android framework (android-all jar) on a plain JVM.
plugins { kotlin("jvm") version "2.0.21" }
repositories { mavenCentral() }
dependencies { compileOnly(files("../jars/android-all.jar")) }
kotlin { jvmToolchain(21) }

val app = file("../../../app/src/main/java/app/personal/clock")
val rootFiles = layout.buildDirectory.dir("rootfiles")

// Single files in the package root (a source dir can't name a single file).
val copyRootFiles = tasks.register<Copy>("copyRootFiles") {
    from(app) { include("Tabs.kt", "ClockApp.kt", "SystemServices.kt") }
    into(rootFiles)
}

sourceSets {
    main {
        kotlin.srcDirs(
            "stubs", rootFiles,
            "$app/core", "$app/data", "$app/alarm", "$app/holiday", "$app/widget",
        )
    }
}
tasks.named("compileKotlin") { dependsOn(copyRootFiles) }
