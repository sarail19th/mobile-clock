fun main() {
    var fail = 0; var pass = 0
    for (c in listOf(app.personal.clock.core.CoreTest::class.java, app.personal.clock.core.WidgetFitTest::class.java, app.personal.clock.core.HolidayAndSkipTest::class.java, app.personal.clock.data.ModelsTest::class.java)) {
        val inst = c.getDeclaredConstructor().newInstance()
        for (m in c.declaredMethods.sortedBy { it.name }) {
            if (m.getAnnotation(org.junit.Test::class.java) == null) continue
            try { m.invoke(inst); pass++; println("PASS ${c.simpleName}.${m.name}") }
            catch (e: java.lang.reflect.InvocationTargetException) { fail++; println("FAIL ${c.simpleName}.${m.name}: ${e.targetException}") }
        }
    }
    println("passed=$pass failed=$fail"); if (fail > 0) System.exit(1)
}
