package mu.location.savmed.ui.locationing

import java.sql.Timestamp

data class liveLocationData(
    var lat : Double,
    var lon : Double,
    var sqlStatus : Int,
    var address : String,
    var Flag : String,
    var time : String,
    var userName : String
)

data class getLiveLocationData(
    val latitude: Double,
    val longitude: Double,
    val userName: String
)

data class locationData(
    var Latitude : Double,
    var Longitude : Double,
    var sqlStatus : Int,
    val timestamp: String,
    var Address : String,
    var CalleruserName : String,
    var ReceiveruserName : String
)

class MapData {
    var routes = ArrayList<Routes>()
}

class Routes {
    var legs = ArrayList<Legs>()
}

class Legs {
    var distance = Distance()
    var duration = Duration()
    var end_address = ""
    var start_address = ""
    var end_location = Location()
    var start_location = Location()
    var steps = ArrayList<Steps>()
}

class Steps {
    var distance = Distance()
    var duration = Duration()
    var end_address = ""
    var start_address = ""
    var end_location =Location()
    var start_location = Location()
    var polyline = PolyLine()
    var travel_mode = ""
    var maneuver = ""
}

class Duration {
    var text = ""
    var value = 0
}

class Distance {
    var text = ""
    var value = 0
}

class PolyLine {
    var points = ""
}

class Location{
    var lat =""
    var lng =""
}