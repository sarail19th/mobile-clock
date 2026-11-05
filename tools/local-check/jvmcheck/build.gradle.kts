// Runs the pure-Kotlin unit tests locally with a tiny JUnit shim (Maven Central rate-limits the real JUnit jar in
// some sandboxes). The android-all jar supplies org.json and the framework types Models/Store only reference.
plugins { kotlin("jvm") version "2.0.21"; application }
repositories { mavenCentral() }
dependencies {
    compileOnly(files("../jars/android-all.jar"))
    runtimeOnly(files("../jars/android-all.jar"))
}
kotlin { jvmToolchain(21) }

val src = file("../../../app/src")
sourceSets {
    main {
        kotlin.srcDirs(
            "$src/main/java/app/personal/clock/core",
            "$src/main/java/app/personal/clock/data",
            "$src/test/java/app/personal/clock/core",
            "$src/test/java/app/personal/clock/data",
            "shim",
        )
    }
}
application { mainClass.set("RunnerKt") }
