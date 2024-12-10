package mu.location.savmed.ui.auth.EmergencyContacts

//data class EmergencyContact(
//    val contact: String,
//    val category: String
//)

data class EmergencyContact(
    val sqlStatus: Int = 0,
    val userName: String,
    val emergencyContact: String
)

data class EmergencyContactResponse(
    val emr_contact_name: String
)