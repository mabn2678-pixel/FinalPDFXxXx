package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_pdfs")
data class RecentPdf(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileName: String,
    val lastPage: Int = 1,
    val totalPages: Int = 0,
    val lastOpened: Long = System.currentTimeMillis()
)
