package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Ignore

@Entity(
    tableName = "recent_pdfs",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class RecentPdf(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileName: String,
    val lastPage: Int = 1,
    val totalPages: Int = 0,
    val lastOpenedTime: Long = 0L
) {
    @get:Ignore
    val lastOpened: Long
        get() = lastOpenedTime
}
