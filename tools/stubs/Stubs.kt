// Minimal type-only stand-ins for the Android/Room APIs used by the data layer. Used ONLY by tools/typecheck_data.sh
// to catch compile errors without an Android SDK. They are never packaged in the app.
package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [], val foreignKeys: Array<ForeignKey> = [], val primaryKeys: Array<String> = [])
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Index(vararg val value: String, val unique: Boolean = false)
annotation class ForeignKey(val entity: KClass<*>, val parentColumns: Array<String>, val childColumns: Array<String>, val onDelete: Int = 0) { companion object { const val CASCADE = 5 } }
annotation class ColumnInfo(val name: String = "")
annotation class Dao
annotation class Insert(val onConflict: Int = 3)
annotation class Update
annotation class Query(val value: String)
object OnConflictStrategy { const val REPLACE = 1; const val IGNORE = 5; const val ABORT = 3 }
annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)
abstract class RoomDatabase { open fun close() {} }
class RoomBuilder<T : RoomDatabase> { fun addMigrations(vararg m: androidx.room.migration.Migration) = this; fun build(): T = throw NotImplementedError() }
object Room { fun <T : RoomDatabase> databaseBuilder(c: android.content.Context, k: Class<T>, name: String) = RoomBuilder<T>() }
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R = block()
