package student.projects.spotmap

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Patterns
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleGoogleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        val registerRedirect = findViewById<TextView>(R.id.registerRedirect)
        val forgotPassword = findViewById<TextView>(R.id.forgotPassword)
        val googleLoginBtn = findViewById<com.google.android.gms.common.SignInButton>(R.id.googleLoginBtn)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // From google-services.json
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Email/Password login
        loginBtn.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val pass = passwordInput.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Enter valid email"
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(pass)) {
                passwordInput.error = "Enter password"
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        loginSuccess()
                    } else {
                        Toast.makeText(this, "Verify your email before logging in", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Google login
        googleLoginBtn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // Forgot password
        forgotPassword.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email to reset password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Register redirect
        registerRedirect.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    loginSuccess()
                } else {
                    Toast.makeText(this, "Google login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loginSuccess() {
        // daily login reward
        val pointsManager = PointsManager()
        pointsManager.giveDailyLoginPoints(this) { success, msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
