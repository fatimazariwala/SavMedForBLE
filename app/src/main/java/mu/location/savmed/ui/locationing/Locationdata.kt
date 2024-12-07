package mu.location.savmed.ui.locationing

data class liveLocationData(
    var lat : Double,
    var lon : Double,
    var sqlStatus : Int,
    var address : String,
    var Flag : String,
    var time : String,
    var userName : String
)

data class locationData(
    var Latitude : Double,
    var Longitude : Double,
    var sqlStatus : Int,
    var Address : String,
    var CalleruserName : String,
    var ReceiveruserName : String
)