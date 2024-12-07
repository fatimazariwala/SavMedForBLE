package mu.location.savmed.ui.medical

import android.widget.EditText

data class MedicalInfo (

    var userName : String? = null,
    var heartProbs: Boolean = false,
    var bloodPressureProb: Boolean = false,
    var lungsProb: Boolean = false,
    var diabeties: Boolean = false,
    var jaundice : Boolean = false,
    var kidney : Boolean = false,
    var seizures : Boolean = false,
    var bleedingExcess : Boolean = false,
    var muscleDisease : Boolean = false,
    var psychiatricProbs : Boolean = false,
    var gender: String? = null,
    var age: Int = 0,
    var bloodGrp : String? = null,
    var allergies : String? = null,
    var medicalNotes: String? = null,
    var chronicIllness : String? = null
) {}
