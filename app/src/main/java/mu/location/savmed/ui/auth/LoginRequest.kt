package mu.location.savmed.ui.auth

data class LoginRequest (
    val priKey: Int,
    val userName : String,
    val password : String,
    val status : String
) { }

data class pri (
    val pri: String
)