package mu.location.savmed.ui.call.viewModels

import android.content.Context

data class OutGoingCallDetails(
    val remoteUri: String,
    val fragmentContext: Context,
    val frag: String?=null
)
