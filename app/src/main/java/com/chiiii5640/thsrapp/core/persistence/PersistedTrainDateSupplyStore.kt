package com.chiiii5640.thsrapp.core.persistence

import android.content.Context
import com.chiiii5640.thsrapp.core.model.TdxTrainDateSupply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PersistedTrainDateSupplySnapshot(
    val savedAtEpochMillis: Long,
    val lastSuccessfulTrainDatesFetchAtEpochMillis: Long? = null,
    val supply: TdxTrainDateSupply,
)

class PersistedTrainDateSupplyStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val file = File(context.filesDir, "PersistedTrainDateSupply.json")

    suspend fun read(): PersistedTrainDateSupplySnapshot? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        json.decodeFromString<PersistedTrainDateSupplySnapshot>(file.readText())
    }

    suspend fun write(snapshot: PersistedTrainDateSupplySnapshot) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(snapshot))
    }
}
