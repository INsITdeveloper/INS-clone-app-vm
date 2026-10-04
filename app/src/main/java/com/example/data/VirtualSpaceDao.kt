package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VirtualSpaceDao {
    @Query("SELECT * FROM cloned_apps ORDER BY folderName ASC, lastLaunchedAt DESC")
    fun getAllClonedApps(): Flow<List<CloneAppEntity>>

    @Query("SELECT * FROM cloned_apps WHERE id = :id LIMIT 1")
    suspend fun getCloneById(id: Int): CloneAppEntity?

    @Query("SELECT * FROM cloned_apps WHERE packageName = :pkg ORDER BY instanceIndex DESC")
    suspend fun getClonesByPackage(pkg: String): List<CloneAppEntity>

    @Query("SELECT COUNT(*) FROM cloned_apps")
    suspend fun getCloneCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClone(clone: CloneAppEntity): Long

    @Update
    suspend fun updateClone(clone: CloneAppEntity)

    @Delete
    suspend fun deleteClone(clone: CloneAppEntity)

    @Query("UPDATE cloned_apps SET isRunning = 0, virtualPid = 0")
    suspend fun stopAllRunningClones()

    @Query("SELECT * FROM ipc_hook_logs ORDER BY timestamp DESC LIMIT 120")
    fun getRecentHookLogs(): Flow<List<IpcHookLogEntity>>

    @Query("SELECT * FROM ipc_hook_logs WHERE cloneId = :cloneId ORDER BY timestamp DESC LIMIT 60")
    fun getHookLogsForClone(cloneId: Int): Flow<List<IpcHookLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHookLog(log: IpcHookLogEntity)

    @Query("DELETE FROM ipc_hook_logs")
    suspend fun clearAllHookLogs()
}
