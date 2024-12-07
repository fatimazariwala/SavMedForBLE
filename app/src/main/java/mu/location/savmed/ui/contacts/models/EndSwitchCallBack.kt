package mu.location.savmed.ui.contacts.models

interface EndSwitchCallBack {

        // Trigger Outgoing Call From contact List
        fun switchToOutgoingCallFragment()

        // Trigger Chat From Contact List
        fun endMainActivity()
}