package student.projects.spotmap.ui.addspot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import student.projects.spotmap.PointsManager
import student.projects.spotmap.R
import student.projects.spotmap.api.ApiClient
import student.projects.spotmap.api.SpotResponse

class AddSpotFragment : Fragment(), OnMapReadyCallback {

    private lateinit var spotName: EditText
    private lateinit var spotDesc: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipGroupExtra: ChipGroup
    private lateinit var chipExpand: Chip
    private lateinit var publicSwitch: Switch
    private lateinit var addPhotosBtn: Button
    private lateinit var submitBtn: Button
    private lateinit var selectedImagesPreview: LinearLayout

    private var googleMap: GoogleMap? = null
    private var marker: Marker? = null
    private var selectedLatLng: LatLng? = null
    private val PICK_IMAGES_REQUEST = 1001
    private val selectedUris = mutableListOf<Uri>()

    private val pointsManager = PointsManager()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    private var username: String = "Unknown"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_add_spot, container, false)

        spotName = root.findViewById(R.id.etSpotName)
        spotDesc = root.findViewById(R.id.etSpotDesc)
        chipGroup = root.findViewById(R.id.chipGroupTags)
        chipGroupExtra = root.findViewById(R.id.chipGroupExtra)
        chipExpand = root.findViewById(R.id.chipExpand)
        publicSwitch = root.findViewById(R.id.switchPublic)
        addPhotosBtn = root.findViewById(R.id.btnAddPhotos)
        submitBtn = root.findViewById(R.id.btnSubmitSpot)
        selectedImagesPreview = root.findViewById(R.id.llSelectedImages)

        addPhotosBtn.setOnClickListener { pickImages() }
        submitBtn.setOnClickListener { submitSpot() }

        // Setup the expand/collapse for extra chips
        chipGroupExtra.visibility = View.GONE
        chipExpand.setOnClickListener {
            if (chipGroupExtra.visibility == View.GONE) {
                chipGroupExtra.visibility = View.VISIBLE
                chipExpand.text = "▲" // Up arrow
            } else {
                chipGroupExtra.visibility = View.GONE
                chipExpand.text = "⋮" // Three dots
            }
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.addSpotMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fetchUsername()

        return root
    }

    private fun fetchUsername() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            database.child("users").child(currentUser.uid).child("username")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        username = snapshot.getValue(String::class.java) ?: "Unknown"
                        Log.d("AddSpot", "Fetched username: $username")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("AddSpot", "Failed to fetch username: ${error.message}")
                        Toast.makeText(requireContext(), "Failed to fetch username", Toast.LENGTH_SHORT).show()
                    }
                })
        } else {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Pictures"), PICK_IMAGES_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGES_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedUris.clear()
            selectedImagesPreview.removeAllViews()
            data?.let {
                if (it.clipData != null) {
                    for (i in 0 until it.clipData!!.itemCount) {
                        val uri = it.clipData!!.getItemAt(i).uri
                        selectedUris.add(uri)
                        addThumbnail(uri)
                    }
                } else if (it.data != null) {
                    val uri = it.data!!
                    selectedUris.add(uri)
                    addThumbnail(uri)
                }
            }
        }
    }

    private fun addThumbnail(uri: Uri) {
        val imageView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply { setMargins(8, 8, 8, 8) }
            setImageURI(uri)
        }
        selectedImagesPreview.addView(imageView)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val startLatLng = LatLng(-33.918861, 18.4233) // Cape Town
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 10f))

        map.setOnMapClickListener { latLng ->
            marker?.remove()
            marker = map.addMarker(MarkerOptions().position(latLng).title("New Spot"))
            selectedLatLng = latLng
        }
    }

    private fun submitSpot() {
        val name = spotName.text.toString().trim()
        val desc = spotDesc.text.toString().trim()
        val isPublic = publicSwitch.isChecked
        val tags = mutableListOf<String>()

        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            if (chip.isChecked) tags.add(chip.text.toString())
        }
        for (i in 0 until chipGroupExtra.childCount) {
            val chip = chipGroupExtra.getChildAt(i) as Chip
            if (chip.isChecked) tags.add(chip.text.toString())
        }

        if (name.isEmpty() || selectedLatLng == null) {
            Toast.makeText(requireContext(), "Name and location required", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = firebaseAuth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isPublic) {
            val privateSpotRef = database.child("users").child(userId).child("privateSpots").push()
            val privateSpotData = mapOf(
                "spotId" to privateSpotRef.key,
                "name" to name,
                "description" to desc,
                "tags" to tags,
                "latitude" to selectedLatLng!!.latitude,
                "longitude" to selectedLatLng!!.longitude,
                "public" to false
            )
            privateSpotRef.setValue(privateSpotData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Private spot saved in Settings!", Toast.LENGTH_SHORT).show()
                    resetForm()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to save private spot: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        // Public spots -> API call
        val userIdBody = RequestBody.create("text/plain".toMediaTypeOrNull(), userId)
        val usernameBody = RequestBody.create("text/plain".toMediaTypeOrNull(), username)
        val nameBody = RequestBody.create("text/plain".toMediaTypeOrNull(), name)
        val descBody = RequestBody.create("text/plain".toMediaTypeOrNull(), desc)
        val tagsBody = RequestBody.create("text/plain".toMediaTypeOrNull(), tags.joinToString(","))
        val latBody = RequestBody.create("text/plain".toMediaTypeOrNull(), selectedLatLng!!.latitude.toString())
        val lngBody = RequestBody.create("text/plain".toMediaTypeOrNull(), selectedLatLng!!.longitude.toString())
        val publicBody = RequestBody.create("text/plain".toMediaTypeOrNull(), isPublic.toString())

        val imageParts = mutableListOf<MultipartBody.Part>()
        for ((index, uri) in selectedUris.withIndex()) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val reqFile = RequestBody.create("image/*".toMediaTypeOrNull(), bytes)
                    val part = MultipartBody.Part.createFormData("images", "photo_$index.jpg", reqFile)
                    imageParts.add(part)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("AddSpot", "Image processing error", e)
            }
        }

        val call = ApiClient.instance.addSpot(
            userIdBody,
            usernameBody,
            nameBody,
            descBody,
            tagsBody,
            latBody,
            lngBody,
            publicBody,
            imageParts
        )

        call.enqueue(object : Callback<SpotResponse> {
            override fun onResponse(call: Call<SpotResponse>, response: Response<SpotResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), "Public spot added!", Toast.LENGTH_SHORT).show()
                    pointsManager.giveSpotPoints(requireContext()) { _, msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                    resetForm()
                } else {
                    Toast.makeText(requireContext(), "Failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SpotResponse>, t: Throwable) {
                Toast.makeText(requireContext(), "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun resetForm() {
        spotName.text.clear()
        spotDesc.text.clear()
        chipGroup.clearCheck()
        chipGroupExtra.clearCheck()
        chipGroupExtra.visibility = View.GONE
        chipExpand.text = "⋮"
        publicSwitch.isChecked = false
        selectedUris.clear()
        selectedImagesPreview.removeAllViews()
        marker?.remove()
        selectedLatLng = null
    }
}
