package com.chiiii5640.thsrapp.core.persistence

import android.content.Context
import com.chiiii5640.thsrapp.core.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoutePreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("route_preferences", Context.MODE_PRIVATE)

    fun load(): Pair<Station, Station> {
        val origin = preferences.getString("origin", null)?.let(Station::fromCode) ?: Station.Taipei
        val destination = preferences.getString("destination", null)?.let(Station::fromCode) ?: Station.Zuoying
        return origin to destination
    }

    suspend fun save(origin: Station, destination: Station) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString("origin", origin.code)
            .putString("destination", destination.code)
            .apply()
    }
}
