package student.projects.spotmap

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var userEmailTextView: TextView
    private lateinit var pointsTextView: TextView
    private lateinit var streakTextView: TextView
    private lateinit var spotsAddedTextView: TextView
    private lateinit var currentAvatarImageView: ImageView
    private lateinit var storeButton: Button

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val pointsManager = PointsManager()
    private val avatarManager = AvatarManager()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        loadUserData()
        loadPointsData()
        loadSpotsAddedCount()

        storeButton.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, StoreFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

    }

    private fun initializeViews(view: View) {
        userEmailTextView = view.findViewById(R.id.userEmailTextView)
        pointsTextView = view.findViewById(R.id.pointsTextView)
        streakTextView = view.findViewById(R.id.streakTextView)
        spotsAddedTextView = view.findViewById(R.id.spotsAddedTextView)
        currentAvatarImageView = view.findViewById(R.id.currentAvatarImageView)
        storeButton = view.findViewById(R.id.storeButton)
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        userEmailTextView.text = currentUser?.email ?: "No email"

        avatarManager.ensureUserProfileExists { success ->
            if (success) {
                avatarManager.getUserProfile { profile ->
                    val avatarResId = profile?.let { avatarManager.getAvatarResourceId(it.currentAvatar) }
                        ?: R.drawable.ic_person
                    currentAvatarImageView.setImageResource(avatarResId)
                }
            } else {
                currentAvatarImageView.setImageResource(R.drawable.ic_person)
            }
        }
    }

    private fun loadPointsData() {
        pointsManager.getCurrentUserPoints { userPoints ->
            if (userPoints != null) {
                pointsTextView.text = "Points: ${userPoints.totalPoints}"
                streakTextView.text = "Streak: ${userPoints.currentStreak} months"
            } else {
                pointsTextView.text = "Points: 0"
                streakTextView.text = "Streak: 0 months"
            }
        }
    }

    private fun loadSpotsAddedCount() {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("users").child(uid).child("spots")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val spotsCount = snapshot.childrenCount.toInt()
                    spotsAddedTextView.text = "Spots Added: $spotsCount"
                }

                override fun onCancelled(error: DatabaseError) {
                    spotsAddedTextView.text = "Spots Added: 0"
                }
            })
    }

    override fun onResume() {
        super.onResume()
        // Refresh all data (points, streak, spots, avatar)
        loadUserData()
        loadPointsData()
        loadSpotsAddedCount()
    }
}
