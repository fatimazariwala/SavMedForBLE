package mu.location.savmed.ui.locationing.DataProcessing

import com.google.android.gms.maps.model.LatLng

interface TaskLoadedCallback {

    fun onTaskDone(startPoint: LatLng?,endPoint: LatLng?,vararg values: Any?)
}