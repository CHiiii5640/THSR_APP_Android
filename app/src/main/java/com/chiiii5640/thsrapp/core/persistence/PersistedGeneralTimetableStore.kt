package com.chiiii5640.thsrapp.core.persistence

import android.content.Context
import com.chiiii5640.thsrapp.core.model.TdxGeneralTimetableRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class PersistedGeneralTimetableStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val file = File(context.filesDir, "PersistedGeneralTimetable.json")

    suspend fun read(): List<TdxGeneralTimetableRecord> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        json.decodeFromString<List<TdxGeneralTimetableRecord>>(file.readText())
    }

    suspend fun write(items: List<TdxGeneralTimetableRecord>) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(items))
    }
}
