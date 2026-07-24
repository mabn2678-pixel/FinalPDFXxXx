package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPdfDao {
    @Query("SELECT * FROM recent_pdfs ORDER BY lastOpened DESC")
    fun getAllRecentPdfs(): Flow<List<RecentPdf>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pdf: RecentPdf)

    @Query("SELECT * FROM recent_pdfs WHERE filePath = :filePath LIMIT 1")
    suspend fun getPdfByPath(filePath: String): RecentPdf?

    @Query("UPDATE recent_pdfs SET lastPage = :lastPage, lastOpened = :lastOpened WHERE filePath = :filePath")
    suspend fun updateLastPage(filePath: String, lastPage: Int, lastOpened: Long = System.currentTimeMillis())

    @Query("DELETE FROM recent_pdfs WHERE filePath = :filePath")
    suspend fun deletePdf(filePath: String)

    @Query("DELETE FROM recent_pdfs")
    suspend fun clearHistory()
}
