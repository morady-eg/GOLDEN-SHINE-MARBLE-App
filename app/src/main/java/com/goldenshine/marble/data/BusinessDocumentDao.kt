package com.goldenshine.marble.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface BusinessDocumentDao {
    @Query("SELECT * FROM business_documents ORDER BY createdAt DESC")
    suspend fun getAll(): List<BusinessDocumentEntity>

    @Query("SELECT * FROM business_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BusinessDocumentEntity?

    @Query("SELECT * FROM business_documents WHERE clientName LIKE '%' || :query || '%' OR documentNumber LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun search(query: String): List<BusinessDocumentEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM business_documents WHERE kind = :kind AND year = :year")
    suspend fun maxSequence(kind: String, year: Int): Int

    @Insert
    suspend fun insert(document: BusinessDocumentEntity): Long

    @Update
    suspend fun update(document: BusinessDocumentEntity)

    @Query("DELETE FROM business_documents WHERE id = :id")
    suspend fun delete(id: Long)
}
