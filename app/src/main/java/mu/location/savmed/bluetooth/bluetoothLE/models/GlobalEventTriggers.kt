package mu.location.savmed.bluetooth.bluetoothLE.models

sealed interface GlobalEventTriggers {

    object ConnectionEstablished: GlobalEventTriggers
    object DataTransferSucceeded: GlobalEventTriggers
    object RingerPermissionError: GlobalEventTriggers
    object LiveLocationCheck: GlobalEventTriggers
    object CallButtonPressed: GlobalEventTriggers
    object DestroyWsSession: GlobalEventTriggers
    object UserNotFound: GlobalEventTriggers

    data class BLETransferSucceeded(val message: String): GlobalEventTriggers
    data class WsMessages(val message: String): GlobalEventTriggers

    data class Error(val message: String): GlobalEventTriggers

    data class Success(val message: String): GlobalEventTriggers

}