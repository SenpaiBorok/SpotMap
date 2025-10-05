package student.projects.spotmap.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import student.projects.spotmap.LoginActivity
import student.projects.spotmap.R

class SettingsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val settingsOptions = listOf("Manage Private Spots", "Logout")
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewSettings)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = SettingsAdapter(settingsOptions) { option ->
            when(option) {
                "Manage Private Spots" -> {
                    // Open the private spots management fragment
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ManagePrivateSpotsFragment())
                        .addToBackStack(null)
                        .commit()
                }
                "Logout" -> {
                    auth.signOut()
                    startActivity(Intent(requireContext(), LoginActivity::class.java)
                        .apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return view
    }
}
