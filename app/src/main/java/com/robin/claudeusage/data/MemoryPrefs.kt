package com.robin.claudeusage.data

import android.content.SharedPreferences

/**
 * CCRM-113 (Sample Port): a [SharedPreferences] that lives in memory and nowhere else — the sample's own copy of the
 * cache and the account registry. Nothing it holds ever reaches a file, so entering the sample cannot write a real
 * setting, and process death drops it with the flag.
 *
 * `apply()` and `commit()` both land at once (there is no disk to wait for); listeners are not supported, because
 * nothing in the app registers one on these stores.
 */
class MemoryPrefs : SharedPreferences {

    private val map = LinkedHashMap<String, Any?>()
    private val lock = Any()

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { LinkedHashMap(map) }

    override fun getString(key: String, defValue: String?): String? =
        synchronized(lock) { map[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) { (map[key] as? Set<String>)?.toMutableSet() ?: defValues }

    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { map[key] as? Int ?: defValue }

    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { map[key] as? Long ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { map[key] as? Float ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        synchronized(lock) { map[key] as? Boolean ?: defValue }

    override fun contains(key: String): Boolean = synchronized(lock) { map.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private val removed = LinkedHashSet<String>()
        private var clear = false

        private fun put(key: String, value: Any?) = apply { pending[key] = value; removed.remove(key) }

        override fun putString(key: String, value: String?) = if (value == null) remove(key) else put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) =
            if (values == null) remove(key) else put(key, values.toSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = apply { pending.remove(key); removed.add(key) }
        override fun clear() = apply { clear = true; pending.clear(); removed.clear() }

        override fun commit(): Boolean {
            synchronized(lock) {
                if (clear) map.clear()
                for (k in removed) map.remove(k)
                map.putAll(pending)
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
