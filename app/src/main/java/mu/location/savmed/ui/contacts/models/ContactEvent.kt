package mu.location.savmed.ui.contacts.models

sealed interface ContactEvent {

    data class ContactError(val message: String) : ContactEvent
    data class ContactNotFound(val message: String) : ContactEvent

    object ContactCreated : ContactEvent
    object ContactEditFound : ContactEvent
    object ContactRemoved : ContactEvent
    object EmptyField : ContactEvent
    object ContactEdited: ContactEvent
    object None : ContactEvent
}