package mu.location.savmed.ui.auth.EmergencyContacts

data class EmergencyContact(
    val contact: String,
    val category: String
)

data class EmergencyContacts(
    val userName: String,
    val emergencyContacts: MutableList<EmergencyContact>
)