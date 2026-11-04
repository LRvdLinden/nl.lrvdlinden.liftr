package nl.lrvdlinden.liftr.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import nl.lrvdlinden.liftr.LiftrApp
import nl.lrvdlinden.liftr.core.LiftrRepository
import nl.lrvdlinden.liftr.core.Prefs
import nl.lrvdlinden.liftr.core.WearSync

/**
 * Phone side of the Wear OS connection (Wearable Data Layer): shares exercises, routines, passes and the
 * last sets with the watch, and takes in workouts finished on the watch. Does nothing without a watch or
 * without Google Play services.
 */
object PhoneWearSync {
    suspend fun push(context: Context) {
        try {
            val repo = LiftrRepository.get(context)
            repo.ensureLoaded()
            val state = WearSync.stateFrom(repo.current, Prefs.unit.raw, Prefs.restDefault, Prefs.userName)
            val request = PutDataMapRequest.create(WearSync.STATE_PATH).apply {
                dataMap.putString(WearSync.KEY_JSON, WearSync.encode(state))
                dataMap.putLong("t", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            Log.i("Liftr", "No watch sync: ${e.message}")
        }
    }

    /** Imports workouts the watch left as data items (also picks up ones that arrived while the app was closed). */
    suspend fun pullWorkouts(context: Context) {
        try {
            val client = Wearable.getDataClient(context)
            val items = client.dataItems.await()
            val uris = mutableListOf<android.net.Uri>()
            try {
                for (item in items) {
                    if (item.uri.path?.startsWith(WearSync.WORKOUT_PATH) != true) continue
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_JSON) ?: continue
                    WearSync.decodeWorkout(json)?.let { importOne(context, it) }
                    uris += item.uri
                }
            } finally {
                items.release()
            }
            for (uri in uris) client.deleteDataItems(uri).await()
        } catch (e: Exception) {
            Log.i("Liftr", "No watch workouts: ${e.message}")
        }
    }

    internal suspend fun importOne(context: Context, w: WearSync.WatchWorkout) {
        val repo = LiftrRepository.get(context)
        repo.ensureLoaded()
        if (repo.current.workouts.containsKey(w.id)) return
        WearSync.importWorkout(repo, w)
        LiftrApp.afterWorkoutChange()
    }
}

class PhoneListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        val found = mutableListOf<Pair<android.net.Uri, String>>()
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path?.startsWith(WearSync.WORKOUT_PATH) != true) continue
            DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_JSON)?.let { found += item.uri to it }
        }
        if (found.isEmpty()) return
        scope.launch {
            for ((uri, json) in found) {
                WearSync.decodeWorkout(json)?.let { PhoneWearSync.importOne(applicationContext, it) }
                try { Wearable.getDataClient(applicationContext).deleteDataItems(uri).await() } catch (_: Exception) {}
            }
        }
    }
}
