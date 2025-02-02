package mu.location.savmed.ui.locationing.DataProcessing

import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import mu.location.savmed.ui.locationing.MapsFragment
import org.json.JSONObject
import java.lang.ref.WeakReference


class PointsParser(mContext: MapsFragment, directionMode: String) :
    AsyncTask<String?, Int?, List<List<HashMap<String, String>>>?>() {
    var taskCallback: TaskLoadedCallback = mContext as TaskLoadedCallback
    var directionMode: String = "driving"

    var startPoint: LatLng ?= null
    var endPoint: LatLng ?= null

    init {
        this.directionMode = directionMode
    }

    override fun doInBackground(vararg jsonData: String?): List<List<HashMap<String, String>>>? {
        val jObject: JSONObject
        var routes: List<List<HashMap<String, String>>>? = null

        try {
            jObject = JSONObject(jsonData[0]!!)
            Log.d("mylog", jsonData[0].toString())
            val parser: DataParser = DataParser()
            Log.d("mylog", parser.toString())

            // Starts parsing data
            routes = parser.parse(jObject)
            Log.d("mylog", "Executing routes")
            Log.d("mylog", routes.toString())
        } catch (e: Exception) {
            Log.d("mylog", e.toString())
            e.printStackTrace()
        }
        return routes
    }


    // Executes in UI thread, after the parsing process
    override fun onPostExecute(result: List<List<HashMap<String, String>>>?) {
        var points: ArrayList<LatLng?>
        var lineOptions: PolylineOptions? = null
        // Traversing through all the routes
        for (i in result!!.indices) {
            points = ArrayList()
            lineOptions = PolylineOptions()
            // Fetching i-th route
            val path = result[i]
            // Fetching all the points in i-th route
            for (j in path.indices) {
                val point = path[j]
                val lat = point["lat"]!!.toDouble()
                val lng = point["lng"]!!.toDouble()
                val position = LatLng(lat, lng)
                points.add(position)
            }
            startPoint = points[0]
            endPoint = points[points.size - 1]
            // Adding all the points in the route to LineOptions
            lineOptions.addAll(points)
            if (directionMode.equals("walking", ignoreCase = true)) {
                lineOptions.width(20f)
                lineOptions.color(Color.BLUE)
            } else {
                lineOptions.width(20f)
                lineOptions.color(Color.BLUE)
            }
            Log.d("mylog", "onPostExecute lineoptions decoded")
        }

        // Drawing polyline in the Google Map for the i-th route
        if (lineOptions != null) {
            //mMap.addPolyline(lineOptions);
            taskCallback.onTaskDone(startPoint,endPoint,lineOptions)
        } else {
            Log.d("mylog", "without Polylines drawn")
        }
    }
}