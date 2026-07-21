package com.example.spending

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- 1. ENTITIES (Tables) ---

@Entity(tableName = "spending")
data class Spending(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTime: Long = System.currentTimeMillis(),
    val merchantName: String,
    val category: String,
    val totalAmount: Float,
    val currency: String,
    val paymentSource: String,
    val note: String,
    val status: String // "DRAFT" or "FINALIZED"
)

@Entity(tableName = "spending_item",
    foreignKeys = [ForeignKey(
        entity = Spending::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("spendingId"),
        onDelete = ForeignKey.CASCADE // If a spending is deleted, delete its items
    )]
)
data class SpendingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(index = true) val spendingId: Int,
    val itemName: String,
    val itemPrice: Float
)

// --- 2. DAO (Queries) ---

@Dao
interface SpendingDao {
    @Insert
    suspend fun insertSpending(spending: Spending): Long

    @Insert
    suspend fun insertSpendingItems(items: List<SpendingItem>): List<Long>

    @Update
    suspend fun updateSpending(spending: Spending)

    @Delete
    suspend fun deleteSpending(spending: Spending)

    @Delete
    suspend fun deleteSpendingItem(item: SpendingItem)

    @Query("SELECT * FROM spending ORDER BY dateTime DESC")
    fun getAllSpendings(): Flow<List<Spending>>

    @Query("SELECT * FROM spending_item ORDER BY id DESC LIMIT 50")
    suspend fun getRecentItems(): List<SpendingItem>

    @Query("SELECT * FROM spending_item WHERE itemName LIKE '%' || :searchQuery || '%' ORDER BY id DESC")
    suspend fun searchItems(searchQuery: String): List<SpendingItem>

    @Query("SELECT * FROM spending_item WHERE spendingId = :spendingId")
    suspend fun getItemsForSpending(spendingId: Int): List<SpendingItem>

    // --- NEW: Distinct queries for dynamic autocomplete options ---
    @Query("SELECT DISTINCT merchantName FROM spending WHERE merchantName IS NOT NULL AND merchantName != '' ORDER BY merchantName ASC")
    suspend fun getDistinctMerchants(): List<String>

    @Query("SELECT DISTINCT category FROM spending WHERE category IS NOT NULL AND category != '' ORDER BY category ASC")
    suspend fun getDistinctCategories(): List<String>

    @Query("SELECT DISTINCT paymentSource FROM spending WHERE paymentSource IS NOT NULL AND paymentSource != '' ORDER BY paymentSource ASC")
    suspend fun getDistinctPaymentSources(): List<String>
}

// --- 3. DATABASE SETUP ---

@Database(entities = [Spending::class, SpendingItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spendingDao(): SpendingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spending_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}