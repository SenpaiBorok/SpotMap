package student.projects.spotmap

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase


class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val confirmPasswordInput = findViewById<EditText>(R.id.confirmPasswordInput)
        val registerBtn = findViewById<Button>(R.id.registerBtn)
        val googleLoginBtn = findViewById<SignInButton>(R.id.googleLoginBtn)
        val loginRedirect = findViewById<TextView>(R.id.loginRedirect)

        loginRedirect.apply {
            setTextColor(resources.getColor(android.R.color.holo_blue_light))
            movementMethod = LinkMovementMethod.getInstance()
        }

        registerBtn.setOnClickListener {
            val uname = usernameInput.text.toString().trim().lowercase()
            val mail = emailInput.text.toString().trim()
            val pass = passwordInput.text.toString().trim()
            val confirmPass = confirmPasswordInput.text.toString().trim()

            // Validation
            if (uname.isEmpty()) { usernameInput.error = "Enter username"; return@setOnClickListener }
            if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { emailInput.error = "Enter valid email"; return@setOnClickListener }
            if (pass.length < 6) { passwordInput.error = "Password must be at least 6 chars"; return@setOnClickListener }
            if (pass != confirmPass) { confirmPasswordInput.error = "Passwords do not match"; return@setOnClickListener }

            // Check if username exists in /users by username
            db.reference.child("users").orderByChild("username").equalTo(uname).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show()
                    } else {
                        createAccount(mail, pass, uname)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Database error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        googleLoginBtn.setOnClickListener { signInWithGoogle() }

        loginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun createAccount(email: String, password: String, username: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.sendEmailVerification()
                val uid = user?.uid ?: return@addOnCompleteListener

                // Save basic info
                val userRef = db.reference.child("users").child(uid)
                val userInfo = mapOf(
                    "username" to username,
                    "email" to email,
                    "provider" to "email"
                )
                userRef.setValue(userInfo)

                // Initialize default profile, points, and unlocked avatar
                initializeUserData(uid) { ok ->
                    if (ok) {
                        Toast.makeText(this, "Registered! Verify your email before logging in.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        // go to Login
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Registered but failed to initialize profile.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializeUserData(uid: String, callback: (Boolean) -> Unit) {
        val userRef = db.reference.child("users").child(uid)

        // Default profile (uses UserProfile dataclass from your project)
        val defaultProfile = UserProfile(
            currentAvatar = AvatarManager.DEFAULT_AVATAR,
            unlockedAvatars = listOf(AvatarManager.DEFAULT_AVATAR),
            displayName = "" // user can update later
        )

        // Default points (start with 50 points)
        val defaultPoints = UserPoints(
            totalPoints = 50,
            pointsHistory = listOf("Initial signup bonus: +50"),

            currentStreak = 0,
            lastStreakUpdate = "",
            totalPointsEarned = 50,
            totalPointsSpent = 0
        )


        // Save profile, points and also ensure avatars list exists
        userRef.child("profile").setValue(defaultProfile)
            .addOnSuccessListener {
                userRef.child("points").setValue(defaultPoints)
                    .addOnSuccessListener {
                        // also add a minimal 'avatars' node to indicate unlocked default avatar
                        val avatarsMap = mapOf(
                            AvatarManager.DEFAULT_AVATAR to mapOf(
                                "id" to AvatarManager.DEFAULT_AVATAR,
                                "name" to "Default Avatar",
                                "price" to 0,
                                "category" to AvatarManager.CATEGORY_BASIC
                            )
                        )
                        userRef.child("avatars").setValue(avatarsMap)
                            .addOnSuccessListener { callback(true) }
                            .addOnFailureListener { callback(false) }
                    }.addOnFailureListener { callback(false) }
            }.addOnFailureListener { callback(false) }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { firebaseAuthWithGoogle(it) }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser ?: return@addOnCompleteListener
                val uid = user.uid
                val uname = user.displayName?.replace(" ", "_")?.lowercase() ?: "user_${uid.take(6)}"
                val userRef = db.reference.child("users").child(uid)

                // Only write basic info if not already present (avoid overwriting existing data)
                userRef.get().addOnSuccessListener { snap ->
                    if (!snap.exists()) {
                        val userData = mapOf(
                            "username" to uname,
                            "email" to user.email,
                            "provider" to "google"
                        )
                        userRef.setValue(userData)
                    }
                    // ensure profile/points initialized
                    initializeUserData(uid) { ok ->
                        // proceed to main screen
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }.addOnFailureListener {
                    // fallback: continue anyway
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            } else {
                Toast.makeText(this, "Google authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
