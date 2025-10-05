// File: student/projects/spotmap/PointsManager.kt
package student.projects.spotmap

import android.content.Context
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

// Data model stored in Firebase
data class UserPoints(
    val totalPoints: Int = 0,
    val lastLoginDate: String = "",
    val spotsAddedToday: Int = 0,
    val lastSpotDate: String = "",
    val totalPointsEarned: Int = 0,
    val totalPointsSpent: Int = 0,
    val currentStreak: Int = 0,                  // streak in months
    val lastStreakUpdate: String = "",           // last date streak was updated
    val pointsHistory: List<String> = emptyList() // history of point events
)


class PointsManager {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    // Configurable constants
    private val INITIAL_POINTS = 50
    private val DAILY_LOGIN_POINTS = 10
    private val SPOT_ADD_POINTS = 25
    private val SPOT_DAILY_LIMIT = 3

    // -----------------------------
    // Get current user points
    // -----------------------------
    fun getCurrentUserPoints(callback: (UserPoints?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(null)
        database.child("users").child(uid).child("points")
            .get().addOnSuccessListener { snap ->
                val points = snap.getValue(UserPoints::class.java) ?: UserPoints()
                callback(points)
            }.addOnFailureListener {
                callback(null)
            }
    }

    // -----------------------------
    // Initialize user with starter points
    // Called inside RegisterActivity after user creation
    // -----------------------------
    fun initializeUserPoints(uid: String, callback: (Boolean) -> Unit) {
        val starter = UserPoints(
            totalPoints = INITIAL_POINTS,
            totalPointsEarned = INITIAL_POINTS,
            totalPointsSpent = 0,
            lastLoginDate = "",
            lastSpotDate = "",
            spotsAddedToday = 0
        )

        database.child("users").child(uid).child("points").setValue(starter)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    // -----------------------------
    // Daily login reward
    // -----------------------------
    fun giveDailyLoginPoints(context: Context, callback: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false, "Not logged in")
        val today = getTodayDate()

        getCurrentUserPoints { userPoints ->
            val points = userPoints ?: UserPoints()

            if (points.lastLoginDate == today) {
                callback(false, "Daily login points already claimed!")
                return@getCurrentUserPoints
            }

            val newTotal = points.totalPoints + DAILY_LOGIN_POINTS
            val updated = points.copy(
                totalPoints = newTotal,
                totalPointsEarned = points.totalPointsEarned + DAILY_LOGIN_POINTS,
                lastLoginDate = today
            )

            database.child("users").child(uid).child("points").setValue(updated)
                .addOnSuccessListener {
                    showToast(context, "You earned $DAILY_LOGIN_POINTS daily login points!")
                    sendNotification(uid, "Daily Reward", "You earned $DAILY_LOGIN_POINTS points for logging in today!")
                    callback(true, "Daily login reward granted")
                }
                .addOnFailureListener {
                    callback(false, "Failed to update points")
                }
        }
    }

    // -----------------------------
    // Add spot points (with daily cap)
    // -----------------------------
    fun giveSpotPoints(context: Context, callback: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false, "Not logged in")
        val today = getTodayDate()

        getCurrentUserPoints { userPoints ->
            val points = userPoints ?: UserPoints()

            if (points.lastSpotDate == today && points.spotsAddedToday >= SPOT_DAILY_LIMIT) {
                callback(false, "Daily spot points limit reached!")
                return@getCurrentUserPoints
            }

            val newTotal = points.totalPoints + SPOT_ADD_POINTS
            val newSpotsToday =
                if (points.lastSpotDate == today) points.spotsAddedToday + 1 else 1

            val updated = points.copy(
                totalPoints = newTotal,
                totalPointsEarned = points.totalPointsEarned + SPOT_ADD_POINTS,
                lastSpotDate = today,
                spotsAddedToday = newSpotsToday
            )

            database.child("users").child(uid).child("points").setValue(updated)
                .addOnSuccessListener {
                    showToast(context, "You earned $SPOT_ADD_POINTS points for adding a spot!")
                    sendNotification(uid, "Spot Added", "You earned $SPOT_ADD_POINTS points!")
                    callback(true, "Spot reward granted")
                }
                .addOnFailureListener {
                    callback(false, "Failed to update points")
                }
        }
    }

    // -----------------------------
    // Spend points (used in Avatar Store)
    // -----------------------------
    fun spendPoints(cost: Int, reason: String, callback: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false, "Not logged in")

        getCurrentUserPoints { userPoints ->
            val points = userPoints ?: UserPoints()
            if (points.totalPoints < cost) {
                return@getCurrentUserPoints callback(false, "Not enough points")
            }

            val updated = points.copy(
                totalPoints = points.totalPoints - cost,
                totalPointsSpent = points.totalPointsSpent + cost
            )

            database.child("users").child(uid).child("points").setValue(updated)
                .addOnSuccessListener {
                    callback(true, "Spent $cost points for $reason")
                }
                .addOnFailureListener {
                    callback(false, "Failed to spend points")
                }
        }
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun showToast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun sendNotification(uid: String, title: String, body: String) {
        // In production: integrate FCM
        println("🔔 Notification to $uid: $title - $body")
    }
}
