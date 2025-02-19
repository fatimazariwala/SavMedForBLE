package mu.location.savmed.websocket

data class joinData (
    val type: String,
    val join: String
)
data class peerDetails (
    val person: String,
    var latitude: Double,
    var longitude: Double,
)

data class peerLatLon (
    var latitude: Double,
    var longitude: Double,
)

data class error (
    val type: String,
    val message : String,
)

