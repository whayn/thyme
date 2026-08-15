package dev.whayn.thyme.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseDao {

    @Query("SELECT * FROM doses ORDER BY time")
    fun observeAll(): Flow<List<Dose>>

    @Query("UPDATE doses SET taken = :taken WHERE id = :id")
    suspend fun setTaken(id: Long, taken: Boolean)

    @Query("SELECT COUNT(*) FROM doses")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(doses: List<Dose>)
}