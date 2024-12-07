package mu.location.savmed.ui.auth

public class registrationDetails {
    private var firstName : String = "";
    private var lastName : String = "";
    private var phnNumber : Long = 0;
    private var address : String = "";
    private var biometricAllow : Boolean = false;
    private var sqlStatus : Int = 0;

    fun setElement (name: String, last : String, phnNumber : Long, address : String, biometricAllow : Boolean) {
        this.firstName = name
        this.lastName = last
        this.phnNumber = phnNumber
        this.address = address
        this.biometricAllow = biometricAllow
    }

    fun printFirstName(): String {
        return firstName;
    }

    fun printBiometric(): Boolean {
         return biometricAllow;
    }

    fun sqlStatus(): Int {
        return sqlStatus;
    }


}