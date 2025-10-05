package student.projects.spotmap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import student.projects.spotmap.api.ApiClient
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var rvSpots: RecyclerView
    private lateinit var spotsList: MutableList<Spot>
    private lateinit var adapter: SpotAdapter
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
                    } ?: Toast.makeText(requireContext(), "No public spots found", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Server error: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<List<Spot>>, t: Throwable) {
                Toast.makeText(requireContext(), "Network error: ${t.message}", Toast.LENGTH_LONG).show()
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

            // Expand section
            val btnExpand: Button = view.findViewById(R.id.btnExpand)
            val llExpanded: LinearLayout = view.findViewById(R.id.llExpandedSection)
            val llComments: LinearLayout = view.findViewById(R.id.llExistingComments)
            val etComment: EditText = view.findViewById(R.id.etNewComment)
            val etRating: EditText = view.findViewById(R.id.etNewRating)
            val btnSubmit: Button = view.findViewById(R.id.btnSubmitCommentRating)
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

            // Expand / collapse
            holder.btnExpand.setOnClickListener {
                if (holder.llExpanded.visibility == View.GONE) {
                    holder.llExpanded.visibility = View.VISIBLE
                    holder.btnExpand.text = "Hide"
                    loadComments(holder, spot)
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

                // Send comment
                if (commentText.isNotEmpty()) {
                    val newComment = Comment(
                        userId = "testUser", // TODO: replace with FirebaseAuth userId
                        username = "testUsername",
                        message = commentText,
                        createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    )

                    ApiClient.instance.addComment(currentSpot.id, newComment)
                        .enqueue(object : Callback<Spot> {
                            override fun onResponse(call: Call<Spot>, response: Response<Spot>) {
                                if (response.isSuccessful) {
                                    spots[pos] = response.body()!!
                                    notifyItemChanged(pos)
                                    holder.etComment.text.clear()
                                    loadComments(holder, spots[pos])
                                    Toast.makeText(requireContext(), "Comment added!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onFailure(call: Call<Spot>, t: Throwable) {
                                Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                }

                // Send rating
                if (ratingValue != null && ratingValue in 1..5) {
                    val body = mapOf("rating" to ratingValue)
                    ApiClient.instance.addRating(currentSpot.id, body)
                        .enqueue(object : Callback<Spot> {
                            override fun onResponse(call: Call<Spot>, response: Response<Spot>) {
                                if (response.isSuccessful) {
                                    spots[pos] = response.body()!!
                                    notifyItemChanged(pos)
                                    holder.etRating.text.clear()
                                    Toast.makeText(requireContext(), "Rating added!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onFailure(call: Call<Spot>, t: Throwable) {
                                Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                }
            }
        }

        private fun loadComments(holder: SpotViewHolder, spot: Spot) {
            holder.llComments.removeAllViews()
            for (c in spot.comments) {
                val tv = TextView(requireContext())
                tv.text = "${c.username}: ${c.message}"
                holder.llComments.addView(tv)
            }
        }
    }
}