package mu.location.savmed.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.utils.RetroFit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RegistrationActivity : AppCompatActivity() {
    lateinit var btn: Button
    lateinit var firstName : EditText
    lateinit var lastName : EditText
   // lateinit var phnNumber : EditText
   // lateinit var address : EditText
    @SuppressLint("UseSwitchCompatOrMaterialCode")
   // lateinit var bioAllow : Switch
    lateinit var apiResponse : TextView

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registration)

        btn = findViewById(R.id.registrationbtn)
        firstName = findViewById(R.id.firstName)
        lastName = findViewById(R.id.lastName)
       // phnNumber = findViewById(R.id.phnNumContent)
//        address = findViewById(R.id.addressContent)
//        bioAllow = findViewById<Switch>(R.id.switch1)
        apiResponse = findViewById(R.id.APIresponse)


        btn.setOnClickListener{

            var name = firstName.getText().toString();
            var last = lastName.getText().toString();
         //   var number = phnNumber.getText().toString().toLong();
        //    var address = address.getText().toString();
         //   var biometricAllow = false
//            if(bioAllow.isChecked) {
//                biometricAllow = true
//            }
            val finalVar = registrationDetails();
            finalVar.setElement(name,last,0,"",false)
            // Log.i("data", finalVar.printElements().toString())

            val gson = Gson();
            var LocJson = gson.toJson(finalVar);
            Log.i("Registration :", LocJson);

            val retrofit = Retrofit.Builder()
                .baseUrl("https://gosaviour.com/wp-json/wdash/v3/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val retrofitAPI = retrofit.create(RetroFit::class.java)

            val call: Call<registrationDetails?>? = retrofitAPI.postData(finalVar)

            call!!.enqueue(object : Callback<registrationDetails?> {
                override fun onResponse(call: Call<registrationDetails?>?, response: Response<registrationDetails?>) {
                    val response: registrationDetails? = response.body()
                    val responseString =
                        "Response Code : " + "\n" + "Name : " + response!!.printFirstName() + " Sql Status : " + response.sqlStatus()

                    apiResponse.setText(responseString);
                    Log.i("API RESPONSE" , responseString.toString())

                    if (response.sqlStatus().equals(1)) {
                        Toast.makeText(this@RegistrationActivity, "Registration Successfull", Toast.LENGTH_SHORT).show();

                        val intent =  Intent(this@RegistrationActivity, LoginActivity :: class.java)
                        startActivity(intent)
                        finish()
                    }
                }
                override fun onFailure(call: Call<registrationDetails?>?, t: Throwable) {

                    Log.i("API ERROR",t.message.toString())
                    Toast.makeText(
                        this@RegistrationActivity,
                        "ERROR Connecting to Network!",
                        Toast.LENGTH_SHORT
                    ).show()
                    apiResponse.setText(t.message);
                }
            })

            Toast.makeText(this, "Registration in Progress", Toast.LENGTH_SHORT).show();
        }
    }

    init {
        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@RegistrationActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        })
    }
}