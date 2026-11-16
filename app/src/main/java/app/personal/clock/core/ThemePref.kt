package app.personal.clock.core

/** The app's own light / dark choice. SYSTEM follows the phone. Pure logic so it can be unit-tested. */
object ThemePref {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    val labels = listOf("System", "Light", "Dark")

    /** Anything unknown (older or damaged data) means SYSTEM. */
    fun sanitize(v: Int): Int = if (v in SYSTEM..DARK) v else SYSTEM

    fun isDark(mode: Int, systemDark: Boolean): Boolean = when (sanitize(mode)) {
        LIGHT -> false
        DARK -> true
        else -> systemDark
    }
}
