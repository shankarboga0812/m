package android.content
open class Context { open val applicationContext: Context get() = this; val assets: android.content.res.AssetManager get() = throw NotImplementedError()
    fun getSharedPreferences(n: String, m: Int): SharedPreferences = throw NotImplementedError()
    companion object { const val MODE_PRIVATE = 0 } }
interface SharedPreferences {
    fun getString(k: String, d: String?): String?; fun getBoolean(k: String, d: Boolean): Boolean; fun getInt(k: String, d: Int): Int
    val all: Map<String, *>; fun edit(): Editor
    interface Editor { fun putString(k: String, v: String?): Editor; fun putBoolean(k: String, v: Boolean): Editor; fun putInt(k: String, v: Int): Editor; fun remove(k: String): Editor; fun apply() }
}
