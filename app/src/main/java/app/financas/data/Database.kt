package app.financas.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun settings(): Flow<Settings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settingsNow(): Settings?

    @Upsert
    suspend fun saveSettings(s: Settings)

    @Query("SELECT * FROM category ORDER BY sort, id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM category ORDER BY sort, id")
    suspend fun categoriesNow(): List<Category>

    @Insert
    suspend fun insertCategory(c: Category): Long

    @Update
    suspend fun updateCategory(c: Category)

    @Query("SELECT * FROM entry ORDER BY epochDay DESC, id DESC")
    fun entries(): Flow<List<Entry>>

    @Query("SELECT * FROM entry ORDER BY epochDay DESC, id DESC")
    suspend fun entriesNow(): List<Entry>

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun entry(id: Long): Entry?

    @Query("SELECT * FROM entry WHERE seriesId = :series")
    suspend fun series(series: String): List<Entry>

    @Insert
    suspend fun insertEntry(e: Entry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreEntries(e: List<Entry>)

    @Update
    suspend fun updateEntry(e: Entry)

    @Delete
    suspend fun deleteEntries(e: List<Entry>)

    @Query("SELECT * FROM fixed_bill ORDER BY dueDay, name")
    fun bills(): Flow<List<FixedBill>>

    @Query("SELECT * FROM fixed_bill ORDER BY dueDay, name")
    suspend fun billsNow(): List<FixedBill>

    @Insert
    suspend fun insertBill(b: FixedBill): Long

    @Update
    suspend fun updateBill(b: FixedBill)

    @Query("SELECT * FROM goal ORDER BY position, id")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM goal ORDER BY position, id")
    suspend fun goalsNow(): List<Goal>

    @Insert
    suspend fun insertGoal(g: Goal): Long

    @Update
    suspend fun updateGoal(g: Goal)

    @Update
    suspend fun updateGoals(g: List<Goal>)

    @Delete
    suspend fun deleteGoal(g: Goal)

    // Restauração de backup: substitui tudo.
    @Query("DELETE FROM entry") suspend fun clearEntries()
    @Query("DELETE FROM category") suspend fun clearCategories()
    @Query("DELETE FROM fixed_bill") suspend fun clearBills()
    @Query("DELETE FROM goal") suspend fun clearGoals()
    @Query("DELETE FROM settings") suspend fun clearSettings()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(c: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBills(b: List<FixedBill>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoals(g: List<Goal>)
}

@Database(
    entities = [Settings::class, Category::class, Entry::class, FixedBill::class, Goal::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FinDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v2: campo "repay" nos lançamentos (resgate a repor / reposição). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entry ADD COLUMN repay INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "financas.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
