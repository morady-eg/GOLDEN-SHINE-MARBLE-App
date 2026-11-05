package com.goldenshine.marble.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "business_documents",
    indices = [Index(value = ["kind", "year", "sequence"], unique = true), Index(value = ["clientName"]), Index(value = ["documentNumber"], unique = true)]
)
data class BusinessDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val year: Int,
    val sequence: Int,
    val documentNumber: String,
    val clientName: String,
    val clientPhone: String,
    val title: String,
    val linesJson: String,
    val vatTreatment: String,
    val vatRate: Double,
    val subtotal: Double,
    val vatAmount: Double,
    val total: Double,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'{}'") val quotationMetaJson: String = "{}"
)
