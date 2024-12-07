package mu.location.savmed.utils

import android.annotation.SuppressLint
import mu.location.savmed.MainActivity
import mu.location.savmed.ui.locationing.LocationActivity
//import mu.location.savmed.sip.SipActivity

@SuppressLint("StaticFieldLeak")
object ActivityHolder {
    lateinit var LocationActivity : LocationActivity
    lateinit var MainActivity : MainActivity
//    lateinit var Siplogin : SipActivity
}