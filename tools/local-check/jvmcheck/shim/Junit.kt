package org.junit
annotation class Test
object Assert {
    @JvmStatic fun assertEquals(a: Any?, b: Any?) { if (a != b) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertEquals(a: Int, b: Int) { if (a != b) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertTrue(c: Boolean) { if (!c) throw AssertionError("expected true") }
    @JvmStatic fun assertTrue(m: String, c: Boolean) { if (!c) throw AssertionError(m) }
    @JvmStatic fun assertEquals(a: Long, b: Long) { if (a != b) throw AssertionError("expected <$a> but was <$b>") }
    @JvmStatic fun assertNull(a: Any?) { if (a != null) throw AssertionError("expected null but was <$a>") }
    @JvmStatic fun assertFalse(c: Boolean) { if (c) throw AssertionError("expected false") }
    @JvmStatic fun assertNotNull(a: Any?) { if (a == null) throw AssertionError("expected non-null") }
}
