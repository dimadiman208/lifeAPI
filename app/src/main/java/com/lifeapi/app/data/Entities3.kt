package com.lifeapi.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Supplement schedule rule (v3). Each supplement has exactly one base rule (base = true) and any number of
 * extra rules. For a given weekday, an extra rule that covers it wins over the base rule (first by sort).
 * days: bitmask Mon=1 … Sun=64.
 * doses: "HH:MM*amount*unit" joined by ";" — e.g. "08:00*1*caps;20:00*2*caps".
 */
@Entity(tableName = "supp_rule", indices = [Index("supplementId")])
data class SuppRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val days: Int,
    @ColumnInfo(defaultValue = "0") val base: Boolean = false,
    val doses: String,
    @ColumnInfo(defaultValue = "0") val sort: Int = 0,
)
