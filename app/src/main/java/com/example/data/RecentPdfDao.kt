package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPdfDao {
    @Query("SELECT * FROM recent_pdfs ORDER BY lastOpenedTime DESC")
    fun getAllRecentPdfs(): Flow<List<RecentPdf>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pdf: RecentPdf)

    @Query("SELECT * FROM recent_pdfs WHERE filePath = :filePath LIMIT 1")
    suspend fun getPdfByPath(filePath: String): RecentPdf?

    @Query("SELECT * FROM recent_pdfs WHERE fileName = :fileName OR fileName = :fileNameWithoutExt LIMIT 1")
    suspend fun getPdfByName(fileName: String, fileNameWithoutExt: String = fileName.replace(".pdf", "", ignoreCase = true)): RecentPdf?

    @Query("UPDATE recent_pdfs SET lastPage = :lastPage, lastOpenedTime = :lastOpenedTime WHERE filePath = :filePath")
    suspend fun updateLastPage(filePath: String, lastPage: Int, lastOpenedTime: Long = System.currentTimeMillis())

    @Query("UPDATE recent_pdfs SET lastOpenedTime = :lastOpenedTime WHERE filePath = :filePath")
    suspend fun updateLastOpenedTime(filePath: String, lastOpenedTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM recent_pdfs WHERE filePath = :filePath")
    suspend fun deletePdf(filePath: String)

    @Query("DELETE FROM recent_pdfs")
    suspend fun clearHistory()
}
