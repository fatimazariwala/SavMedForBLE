package mu.location.savmed.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.SavMed.Companion.coreContext
import mu.location.savmed.SavMed.Companion.corePreferences
import mu.location.savmed.ui.main.home.RippleFragment.Companion.TAG
import mu.location.savmed.utils.RetroFit
//import mu.location.savmed.sip.services.SipService
import org.linphone.core.Factory
import org.linphone.core.TransportType
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {

    lateinit var EmailEdt : EditText
    lateinit var PwdEdt : EditText
    lateinit var domainEdt : EditText

    lateinit var LoginBtn : Button

    lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val SHARED_PREFS = "shared_prefs"
        const val USERNAME_KEY = "username_key"
        const val PASSWORD_KEY = "password_key"
        const val DOMAIN_KEY   = "domain_key"
        const val PRI_KEY = "pri_key"
    }

    var usernameSIP = ""
    var passwordSIP = ""
    var domainSIP = ""
    var priKey = ""

    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_login)

        EmailEdt = findViewById(R.id.idEdtEmail)
        PwdEdt = findViewById(R.id.idEdtPassword)

        LoginBtn = findViewById(R.id.idBtnLogin)

        sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

        usernameSIP = sharedPreferences.getString(USERNAME_KEY, "").toString()
        passwordSIP = sharedPreferences.getString(PASSWORD_KEY, "").toString()
        domainSIP = sharedPreferences.getString(DOMAIN_KEY, "").toString()


        findViewById<Button>(R.id.btnHome).setOnClickListener() {
            val i = Intent(this@LoginActivity, MainActivity::class.java)
            startActivity(i)
            finish();
        }

        LoginBtn.setOnClickListener() {

            if(TextUtils.isEmpty(EmailEdt.getText().toString()) && TextUtils.isEmpty(PwdEdt.getText().toString())){
                Toast.makeText(this,"Please Enter Email and Password",Toast.LENGTH_SHORT).show()
            }
            else{
                val username = EmailEdt.text.toString().trim()
                val password = PwdEdt.text.toString().trim()

                    Toast.makeText(this,"Login In-Progress!",Toast.LENGTH_SHORT).show()
                    val retrofit = Retrofit.Builder()
                        .baseUrl("")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    val retrofitAPI = retrofit.create(RetroFit::class.java)

                    val loginreq = LoginRequest(0,username,password,"")

                    val gson = Gson();
                    var Logreq = gson.toJson(loginreq);
                    Log.i("Login DAta :", Logreq);

                    val call: Call<LoginRequest?>? = retrofitAPI.postLoginRequest(loginreq)

                    call!!.enqueue(object : Callback<LoginRequest?> {
                        override fun onResponse(
                            call: Call<LoginRequest?>,
                            response: Response<LoginRequest?>
                        ) {
                            val responsez: LoginRequest? = response.body()                                  // Passing Response Class

                            val responseString =
                                "Response Code : " + response.code() + "\n" + responsez?.status            // Creating Response String

                            Log.i("API RESPONSE login:" , " ${response.code().toString()},$responsez")

                            if ( responsez?.status == "OK") {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Login Success !!!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                coreContext.postOnCoreThread { core ->

                                    core.transports.udpPort = 5060
                                    core.isIpv6Enabled = false
                                    core.isPushNotificationEnabled = true
                                    core.loadConfigFromXml(corePreferences.savMedDefaultValuesPath)

                                    val authInfo = Factory.instance()
                                        .createAuthInfo(username, null, password, null, "x.x.x.x", "x.x.x.x", null)

                                    val params = core.createAccountParams()
                                    val identity = Factory.instance().createAddress("sip:$username@x.x.x.x")


                                    params.identityAddress = identity
                                    params.isPublishEnabled = true

                                    val address = Factory.instance().createAddress("sip:x.x.x.x")
                                    address?.transport = TransportType.Udp

                                    val proxy = params.setServerAddress(address)

                                    Log.i("[Login Activity]","Srever addres result $proxy")
                                   // params.isOutboundProxyEnabled = true
                                    //core.isForcedIceRelayEnabled = true
                                    Log.i("[Login Activity]","In do upnp ip: ${core.upnpExternalIpaddress} state: ${core.upnpState} available: ${core.upnpAvailable()}")
                                    params.isRegisterEnabled = true

                                    for (info in core.authInfoList) {
                                        Log.i(TAG,"One info before  ${info.username}")
                                    }

                                    val account = core.createAccount(params)
                                    params.pushNotificationAllowed = true
                                    params.isRtpBundleEnabled = false

                                    core.addAuthInfo(authInfo)
                                    core.addAccount(account)
                                    core.isKeepAliveEnabled = true
                                    core.defaultAccount = account

                                    coreContext.contactsManager.getInstituteContactsFromEndpoint()
                                    for (info in core.authInfoList) {
                                        Log.i(TAG,"One info after----${info.username}")
                                    }

                                    Log.i("MAIN ACTIVITY","Auto token ${authInfo.username} ${authInfo.password} ${authInfo.realm} ${authInfo.availableAlgorithms.size} ${authInfo.nativePointer}")

                                    Log.i("SIPService", "Login successful.")
                                }

                                if (coreContext.core.defaultAccount != null) {
                                    Toast.makeText(this@LoginActivity,"SIP Login Successful!",Toast.LENGTH_SHORT).show()
                                }

                                if (usernameSIP.equals("") || passwordSIP.equals("")) {
                                    val editor: SharedPreferences.Editor = sharedPreferences.edit()

                                    editor.putString(USERNAME_KEY,EmailEdt.getText().toString())
                                    editor.putString(PASSWORD_KEY, PwdEdt.getText().toString())
                                    editor.putString(PRI_KEY,responsez.priKey.toString())
                                    editor.apply()
                                }

                                val i = Intent(this@LoginActivity, MainActivity::class.java)
                                startActivity(i)
                                finish();

                            } else { Toast.makeText(
                                this@LoginActivity,
                                "User NOT FOUND !!!",
                                Toast.LENGTH_SHORT
                            ).show() }
                        }

                        override fun onFailure(call: Call<LoginRequest?>, t: Throwable) {
                            Toast.makeText(
                                this@LoginActivity,
                                "ERROR Connecting to Network!",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.i("API ERROR Loginzz",t.message.toString())
                        }
                    })

            }
        }
    }

    override fun onStart() {
        super.onStart()

        if(!usernameSIP.equals("") && !passwordSIP.equals("")  && !domainSIP.equals("")) {
            val i = Intent (this@LoginActivity, MainActivity::class.java)
            startActivity(i)
            finish()
        }
    }
}
