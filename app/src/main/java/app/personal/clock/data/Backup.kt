package app.personal.clock.data

import android.content.Context
import app.personal.clock.core.AlarmItem
import app.personal.clock.core.CustomDay
import org.json.JSONArray
import org.json.JSONObject

/** Export/import of everything the user configured, as one JSON document. */
object Backup {
    private const val VERSION = 1

    fun export(ctx: Context): String {
        val store = Store.get(ctx)
        val alarms = JSONArray().also { arr -> store.alarms().forEach { arr.put(Store.alarmToJson(it)) } }
        val custom = JSONArray().also { arr -> store.customDays().forEach { arr.put(Store.customDayToJson(it)) } }
        val world = JSONArray().also { arr -> store.world().forEach { arr.put(it) } }
        val widgets = JSONObject().also { o ->
            store.allWidgetConfigs().forEach { (id, cfg) -> o.put(id.toString(), cfg.toJson()) }
        }
        return JSONObject()
            .put("app", "personal-clock")
            .put("version", VERSION)
            .put("settings", store.settings().toJson())
            .put("alarms", alarms)
            .put("world", world)
            .put("customDays", custom)
            .put("widgetDefault", store.widgetDefault().toJson())
            // Widget ids are not portable between installs; they are kept for reference only.
            .put("widgets", widgets)
            .toString(2)
    }

    /** Returns null on success, or a human-readable error. Caller must reschedule alarms afterwards. */
    fun restore(ctx: Context, text: String): String? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return "This file is not valid JSON."
        if (root.optString("app") != "personal-clock") return "This is not a Personal Clock backup."
        if (root.optInt("version", 0) > VERSION) return "Backup was made by a newer version of the app."
        val store = Store.get(ctx)
        root.optJSONObject("settings")?.let { store.saveSettings(AppSettings.fromJson(it)) }
        root.optJSONArray("alarms")?.let { arr ->
            val list: List<AlarmItem> = arr.mapObjects { Store.alarmFromJson(it) }
            store.replaceAlarms(list)
        }
        root.optJSONArray("customDays")?.let { arr ->
            val list: List<CustomDay> = arr.mapObjects { Store.customDayFromJson(it) }
            store.replaceCustomDays(list)
        }
        root.optJSONArray("world")?.let { arr ->
            val zones = (0 until arr.length()).mapNotNull { runCatching { arr.getString(it) }.getOrNull() }
            if (zones.isNotEmpty()) store.saveWorld(zones)
        }
        root.optJSONObject("widgetDefault")?.let { store.saveWidgetDefault(WidgetConfig.fromJson(it)) }
        return null
    }
}
