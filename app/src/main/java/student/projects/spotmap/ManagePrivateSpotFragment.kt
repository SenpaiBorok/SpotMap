package student.projects.spotmap.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import student.projects.spotmap.R
import student.projects.spotmap.api.ApiClient
import student.projects.spotmap.api.SpotResponse

class ManagePrivateSpotsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PrivateSpotsAdapter
    private val privateSpotsList = mutableListOf<Map<*, *>>()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var realtimeDB: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_manage_private_spots, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewPrivateSpots)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = PrivateSpotsAdapter(
            privateSpotsList,
            onMakePublic = { spotId, spot -> confirmMakePublic(spotId, spot) },
            onDelete = { spotId -> confirmDeleteSpot(spotId) }
        )
        recyclerView.adapter = adapter

        realtimeDB = FirebaseDatabase.getInstance().reference
        loadPrivateSpots()

        return view
    }

    private fun loadPrivateSpots() {
        val uid = auth.currentUser?.uid ?: return
        realtimeDB.child("users").child(uid).child("privateSpots")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    privateSpotsList.clear()
                    for (child in snapshot.children) {
                        val spot = child.value as? Map<*, *> ?: continue
                        val updatedSpot = spot.toMutableMap()
                        updatedSpot["id"] = child.key
                        privateSpotsList.add(updatedSpot)
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load private spots", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun confirmDeleteSpot(spotId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Spot")
            .setMessage("Are you sure you want to delete this private spot?")
            .setPositiveButton("Delete") { _, _ -> deleteSpot(spotId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSpot(spotId: String) {
        val uid = auth.currentUser?.uid ?: return
        realtimeDB.child("users").child(uid).child("privateSpots").child(spotId)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Spot deleted!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to delete spot: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmMakePublic(spotId: String, spot: Map<*, *>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Make Public")
            .setMessage("Are you sure you want to make this spot public?")
            .setPositiveButton("Yes") { _, _ -> makeSpotPublic(spotId, spot) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun makeSpotPublic(spotId: String, spot: Map<*, *>) {
        val uid = auth.currentUser?.uid ?: return

        // Get username from Firebase
        realtimeDB.child("users").child(uid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.getValue(String::class.java) ?: "Unknown"

                    val name = spot["name"]?.toString() ?: "Unnamed"
                    val desc = spot["description"]?.toString() ?: ""
                    val tags = (spot["tags"] as? List<*>)?.map { it.toString() } ?: emptyList()
                    val latitude = (spot["latitude"] as? Double) ?: 0.0
                    val longitude = (spot["longitude"] as? Double) ?: 0.0

                    val userIdBody = uid.toRequestBody("text/plain".toMediaTypeOrNull())
                    val usernameBody = username.toRequestBody("text/plain".toMediaTypeOrNull())
                    val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
                    val descBody = desc.toRequestBody("text/plain".toMediaTypeOrNull())
                    val tagsBody = tags.joinToString(",").toRequestBody("text/plain".toMediaTypeOrNull())
                    val latBody = latitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val lngBody = longitude.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val publicBody = "true".toRequestBody("text/plain".toMediaTypeOrNull())

                    val imageParts = mutableListOf<MultipartBody.Part>() // Add images if needed

                    ApiClient.instance.addSpot(
                        userIdBody,
                        usernameBody,
                        nameBody,
                        descBody,
                        tagsBody,
                        latBody,
                        lngBody,
                        publicBody,
                        imageParts
                    ).enqueue(object : Callback<SpotResponse> {
                        override fun onResponse(call: Call<SpotResponse>, response: Response<SpotResponse>) {
                            if (response.isSuccessful && response.body()?.success == true) {
                                // Remove from private spots
                                realtimeDB.child("users").child(uid)
                                    .child("privateSpots").child(spotId).removeValue()
                                Toast.makeText(requireContext(), "Spot made public!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), "Failed to make spot public", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<SpotResponse>, t: Throwable) {
                            Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
