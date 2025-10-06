package student.projects.spotmap

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import student.projects.spotmap.api.ApiClient
import java.text.SimpleDateFormat
import java.util.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeFragment : Fragment() {

    private lateinit var rvSpots: RecyclerView
    private lateinit var spotsList: MutableList<Spot>
    private lateinit var adapter: SpotAdapter
    private val db = FirebaseFirestore.getInstance()
    private val googleMapsApiKey = "AIzaSyBV_1ggMnx_5NOMi34eMzPqspaz2oeURCU"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        rvSpots = root.findViewById(R.id.rvPublicSpots)
        rvSpots.layoutManager = LinearLayoutManager(requireContext())
        spotsList = mutableListOf()
        adapter = SpotAdapter(spotsList)
        rvSpots.adapter = adapter

        loadPublicSpots()

        return root
    }

    private fun loadPublicSpots() {
        ApiClient.instance.getPublicSpots().enqueue(object : Callback<List<Spot>> {
            override fun onResponse(call: Call<List<Spot>>, response: Response<List<Spot>>) {
                if (response.isSuccessful) {
                    response.body()?.let { spots ->
                        spotsList.clear()
                        spotsList.addAll(spots)
                        adapter.notifyDataSetChanged()
                        Log.d("HomeFragment", "Loaded ${spots.size} spots from API")
                    } ?: run {
                        Toast.makeText(requireContext(), "No public spots found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Server error: ${response.code()}", Toast.LENGTH_LONG).show()
                    Log.e("HomeFragment", "API error: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<List<Spot>>, t: Throwable) {
                Toast.makeText(requireContext(), "Network error: ${t.message}", Toast.LENGTH_LONG).show()
                Log.e("HomeFragment", "Network failure", t)
            }
        })
    }

    inner class SpotAdapter(private val spots: MutableList<Spot>) :
        RecyclerView.Adapter<SpotAdapter.SpotViewHolder>() {

        inner class SpotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSpotName)
            val tvUploader: TextView = view.findViewById(R.id.tvUploader)
            val tvDesc: TextView = view.findViewById(R.id.tvDescription)
            val tvTags: TextView = view.findViewById(R.id.tvTags)
            val llPhotos: LinearLayout = view.findViewById(R.id.llPhotos)
            val tvLatLng: TextView = view.findViewById(R.id.tvLatLng)
            val ivMiniMap: ImageView = view.findViewById(R.id.ivMiniMap)

            val btnExpand: Button = view.findViewById(R.id.btnExpand)
            val llExpanded: LinearLayout = view.findViewById(R.id.llExpandedSection)
            val llComments: LinearLayout = view.findViewById(R.id.llExistingComments)
            val etComment: EditText = view.findViewById(R.id.etNewComment)
            val etRating: EditText = view.findViewById(R.id.etNewRating)
            val btnSubmit: Button = view.findViewById(R.id.btnSubmitCommentRating)

            val tvStats: TextView = view.findViewById(R.id.tvStats)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.spot_item, parent, false)
            return SpotViewHolder(view)
        }

        override fun getItemCount(): Int = spots.size

        override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
            val spot = spots[position]

            holder.tvName.text = spot.name
            holder.tvUploader.text = "Uploaded by: ${spot.username}"
            holder.tvDesc.text = spot.description
            holder.tvTags.text = "Tags: ${spot.vibeTags.joinToString(", ")}"
            holder.tvLatLng.text = "Location: ${spot.latitude}, ${spot.longitude}"

            // Photos
            holder.llPhotos.removeAllViews()
            for (photoUrl in spot.images) {
                val iv = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                        setMargins(8, 8, 8, 8)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                Glide.with(requireContext()).load(photoUrl).into(iv)
                holder.llPhotos.addView(iv)
            }

            // Static map
            val staticMapUrl =
                "https://maps.googleapis.com/maps/api/staticmap?" +
                        "center=${spot.latitude},${spot.longitude}&zoom=15&size=300x150&markers=color:red%7C${spot.latitude},${spot.longitude}&key=$googleMapsApiKey"
            Glide.with(requireContext()).load(staticMapUrl).into(holder.ivMiniMap)

            // Load stats
            loadStats(holder, spot.id)

            // Expand/collapse comments
            holder.btnExpand.setOnClickListener {
                if (holder.llExpanded.visibility == View.GONE) {
                    holder.llExpanded.visibility = View.VISIBLE
                    holder.btnExpand.text = "Hide"
                    loadComments(holder, spot.id)
                } else {
                    holder.llExpanded.visibility = View.GONE
                    holder.btnExpand.text = "Show More"
                }
            }

            // Submit comment + rating
            holder.btnSubmit.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val currentSpot = spots[pos]

                val commentText = holder.etComment.text.toString().trim()
                val ratingValue = holder.etRating.text.toString().toIntOrNull()

                val user = FirebaseAuth.getInstance().currentUser
                val userId = user?.uid ?: "anonymous"

                if (commentText.isNotEmpty()) {
                    // Get username from Firestore users collection
                    getUserName(userId, user?.email) { username ->
                        submitComment(currentSpot.id, commentText, userId, username, holder)
                    }
                }

                // Submit rating
                if (ratingValue != null && ratingValue in 1..5) {
                    submitRating(currentSpot.id, userId, ratingValue, holder)
                }
            }
        }

        /**
         * Get username from Firestore users collection with fallback to email
         */
        private fun getUserName(userId: String, userEmail: String?, callback: (String) -> Unit) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { userDoc ->
                    val username = if (userDoc.exists() && userDoc.contains("username")) {
                        userDoc.getString("username") ?: getFallbackUsername(userEmail)
                    } else {
                        getFallbackUsername(userEmail)
                    }
                    callback(username)
                }
                .addOnFailureListener {
                    // If Firestore fails, use email fallback
                    callback(getFallbackUsername(userEmail))
                }
        }

        /**
         * Fallback username generation from email
         */
        private fun getFallbackUsername(userEmail: String?): String {
            return userEmail?.substringBefore("@") ?: "Anonymous_${System.currentTimeMillis().toString().takeLast(4)}"
        }

        /**
         * Submit comment to Firestore
         */
        private fun submitComment(spotId: String, commentText: String, userId: String, username: String, holder: SpotViewHolder) {
            val newComment = mapOf(
                "userId" to userId,
                "username" to username,
                "message" to commentText,
                "createdAt" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )

            db.collection("spots")
                .document(spotId)
                .update("comments", FieldValue.arrayUnion(newComment))
                .addOnSuccessListener {
                    holder.etComment.text.clear()
                    loadComments(holder, spotId)
                    loadStats(holder, spotId)
                    Toast.makeText(requireContext(), "Comment added!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("HomeFragment", "Error adding comment", e)
                }
        }

        /**
         * Submit rating to Firestore
         */
        private fun submitRating(spotId: String, userId: String, ratingValue: Int, holder: SpotViewHolder) {
            val ratingMapPath = "ratings.$userId"
            db.collection("spots")
                .document(spotId)
                .update(ratingMapPath, ratingValue)
                .addOnSuccessListener {
                    holder.etRating.text.clear()
                    loadStats(holder, spotId)
                    Toast.makeText(requireContext(), "Rating added!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("HomeFragment", "Error adding rating", e)
                }
        }

        /**
         * Load comments for a given spotId from Firestore
         */
        private fun loadComments(holder: SpotViewHolder, spotId: String) {
            db.collection("spots").document(spotId).get()
                .addOnSuccessListener { doc ->
                    val rawComments = doc.get("comments")
                    holder.llComments.removeAllViews()

                    if (rawComments is List<*>) {
                        for (c in rawComments) {
                            if (c is Map<*, *>) {
                                val username = c["username"] as? String ?: "Unknown"
                                val message = c["message"] as? String ?: ""
                                val tv = TextView(requireContext()).apply {
                                    text = "$username: $message"
                                    setPadding(0, 8, 0, 8)
                                    textSize = 14f
                                }
                                holder.llComments.addView(tv)
                            } else if (c is Comment) {
                                val tv = TextView(requireContext()).apply {
                                    text = "${c.username}: ${c.message}"
                                    setPadding(0, 8, 0, 8)
                                    textSize = 14f
                                }
                                holder.llComments.addView(tv)
                            }
                        }
                    }

                    // If no comments, show message
                    if (holder.llComments.childCount == 0) {
                        val tv = TextView(requireContext()).apply {
                            text = "No comments yet"
                            setPadding(0, 8, 0, 8)
                            textSize = 14f
                            setTextColor(resources.getColor(android.R.color.darker_gray, null))
                        }
                        holder.llComments.addView(tv)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Error loading comments", e)
                    val tv = TextView(requireContext()).apply {
                        text = "Error loading comments"
                        setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    }
                    holder.llComments.addView(tv)
                }
        }

        /**
         * Load ratings & comments count for a spot
         */
        private fun loadStats(holder: SpotViewHolder, spotId: String) {
            db.collection("spots").document(spotId).get()
                .addOnSuccessListener { doc ->
                    val commentsRaw = doc.get("comments") as? List<*>
                    val commentCount = commentsRaw?.size ?: 0

                    val ratingsRaw = doc.get("ratings") as? Map<*, *>
                    val ratingValues = mutableListOf<Double>()

                    if (ratingsRaw != null) {
                        for (value in ratingsRaw.values) {
                            when (value) {
                                is Number -> ratingValues.add(value.toDouble())
                                is String -> value.toDoubleOrNull()?.let { ratingValues.add(it) }
                            }
                        }
                    }

                    val ratingCount = ratingValues.size
                    val avg = if (ratingValues.isNotEmpty()) ratingValues.average() else 0.0

                    holder.tvStats.text = "💬 $commentCount comments | ⭐ ${"%.1f".format(avg)} ($ratingCount ratings)"
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Error loading stats", e)
                    holder.tvStats.text = "💬 0 comments | ⭐ 0.0 (0 ratings)"
                }
        }
    }
}