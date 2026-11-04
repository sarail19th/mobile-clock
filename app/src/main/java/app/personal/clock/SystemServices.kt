package app.personal.clock

import android.content.Context

/** getSystemService(Class) is @Nullable in newer SDKs; these services always exist on a real device. */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> Context.sys(): T = getSystemService(T::class.java) as T
