package mu.location.savmed.ui.medical

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import mu.location.savmed.MainActivity
import mu.location.savmed.R
import mu.location.savmed.ui.call.CallActivity
import mu.location.savmed.utils.RetroFit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MedicalInfoActivity : AppCompatActivity() {

    lateinit var sharedPreferences: SharedPreferences
    companion object {
        const val SHARED_PREFS = "shared_prefs"
        const val USERNAME_KEY = "username_key"
    }

    private var heartProbs  = false
    private var bloodPressureProb = false
    private var lungsProb = false
    private var diabeties = false
    private var jaundice = false
    private var kidney = false
    private var seizures = false
    private var bleedingExcess = false
    private var muscleDisease = false
    private var psychiatricProbs = false
    private var gender = ""
    private var age = 0
    private var bloodGrp = ""
    private var allergies = ""
    private var medicalNotes = ""
    private var chronicIllness = ""
    var usernameLogin = ""

    lateinit var saveBtn : Button


    private val navListener = BottomNavigationView.OnNavigationItemSelectedListener {
        // By using switch we can easily get the
        // selected fragment by using there id
        var selectedFragment: Fragment? = null
        when (it.itemId) {
            R.id.main_home -> {
                val i = Intent(applicationContext,MainActivity::class.java)
                i.putExtra("frag",1)
                startActivity(i)
                return@OnNavigationItemSelectedListener true
            }
            R.id.call -> {
                startActivity(Intent(applicationContext, CallActivity::class.java))
                overridePendingTransition(0, 0)
                return@OnNavigationItemSelectedListener true
            }
            R.id.nearBy -> {
                val i = Intent(applicationContext,MainActivity::class.java)
                i.putExtra("frag",2)
                startActivity(i)
                return@OnNavigationItemSelectedListener true
            }
            R.id.medical -> {
                return@OnNavigationItemSelectedListener true
            }
        }
        // It will help to replace the
        // one fragment to other.
        if (selectedFragment != null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, selectedFragment).commit()
        }
        true
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_medical_info)

        sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

        usernameLogin = sharedPreferences.getString(USERNAME_KEY, "").toString()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnNavigationItemSelectedListener(navListener)

        bottomNav.id = R.id.medical
//        Log.i("UserNamezzzzzzzz",usernameLogin)

        saveBtn = findViewById(R.id.buttonSave)

        saveBtn.setOnClickListener() {

            val i = Intent(this@MedicalInfoActivity, MainActivity::class.java)
            startActivity(i)

            Log.i("Save Button","Im clicked")
            if (findViewById<RadioButton>(R.id.heart_yes).isChecked == true) {
                heartProbs = true
            }
            if (findViewById<RadioButton>(R.id.bp_yes).isChecked == true) {
                bloodPressureProb = true
            }
            if (findViewById<RadioButton>(R.id.lung_yes).isChecked == true) {
                lungsProb = true
            }
            if (findViewById<RadioButton>(R.id.diabetes_yes).isChecked == true) {
                diabeties = true
            }
            if (findViewById<RadioButton>(R.id.jaundice_yes).isChecked == true) {
                jaundice = true
            }
            if (findViewById<RadioButton>(R.id.kidney_yes).isChecked == true) {
                kidney = true
            }
            if (findViewById<RadioButton>(R.id.seizures_yes).isChecked == true) {
                seizures = true
            }
            if (findViewById<RadioButton>(R.id.bleeding_yes).isChecked == true) {
                bleedingExcess = true
            }
            if (findViewById<RadioButton>(R.id.muscle_yes).isChecked == true) {
                muscleDisease = true
            }
            if (findViewById<RadioButton>(R.id.psychiatric_yes).isChecked == true) {
                psychiatricProbs = true
            }

            gender = findViewById<EditText>(R.id.gender).getText().toString()
            age = findViewById<EditText>(R.id.age).text.toString().toInt()
            bloodGrp = findViewById<EditText>(R.id.bloodGrp).getText().toString()
            allergies = findViewById<EditText>(R.id.allergies).getText().toString()
            medicalNotes = findViewById<EditText>(R.id.medicalNotes).getText().toString()
            chronicIllness = findViewById<EditText>(R.id.chronicIllness).getText().toString()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://gosaviour.com/wp-json/wdash/v5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val retrofitAPI = retrofit.create(RetroFit::class.java)

            val saveHealthInfo = MedicalInfo(usernameLogin,heartProbs,bloodPressureProb,lungsProb,diabeties,jaundice, kidney, seizures, bleedingExcess, muscleDisease, psychiatricProbs,gender, age, bloodGrp, allergies, medicalNotes, chronicIllness,)

//            Log.i("Return from heathInfo",saveHealthInfo.toString())
//            val gson = Gson();
//            var LocJson = gson.toJson(saveHealthInfo);
//            Log.i("health Info :", LocJson);

            val call: Call<MedicalInfo?>? = retrofitAPI.postMedicalData(saveHealthInfo)

            call!!.enqueue(object : Callback<MedicalInfo?> {
                override fun onResponse(
                    call: Call<MedicalInfo?>,
                    response: Response<MedicalInfo?>
                ) {

                     //passing response to our modal class.
                    val responsez: MedicalInfo? = response.body()

                     //on below line we are getting our data from modal class
                    // and adding it to our string.
                    val responseString =
                        "Response Code : " + response.code() + "\n" + responsez!!.heartProbs + "\n" + responsez.medicalNotes

                     //below line we are setting our
                    // string to our text view.
                    Log.i("API RESPONSE" , response.code().toString())

                    if (response.code() == 200) {
                        Toast.makeText(
                            this@MedicalInfoActivity,
                            "Medical Data Updated !!!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish();
                    } else {
                        Toast.makeText(
                            this@MedicalInfoActivity,
                            "Medical Data Update Failed !!!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish();
                    }
                }

                override fun onFailure(call: Call<MedicalInfo?>, t: Throwable) {

                    Log.i("API ERROR",t.message.toString())
                    finish();
                }
            })

        }

    }

    init {
        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@MedicalInfoActivity,MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        })
    }
}