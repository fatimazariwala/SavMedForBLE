package mu.location.savmed.ui.auth

data class LoginRequest (
    val userName : String,
    val password : String,
    val status : String
) { }