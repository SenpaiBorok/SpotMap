package student.projects.spotmap

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StoreFragment : Fragment(R.layout.fragment_store) {
    private lateinit var pointsTextView: TextView
    private lateinit var avatarRecyclerView: RecyclerView
    private lateinit var categorySpinner: Spinner
    private lateinit var refreshButton: Button

    private val pointsManager = PointsManager()
    private val avatarManager = AvatarManager()

    private var currentUserPoints = 0
    private var userProfile: UserProfile? = null
    private var allAvatars = listOf<Avatar>()
    private var storeAdapter: AvatarStoreAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupCategoryFilter()
        loadData()
        refreshButton.setOnClickListener { loadData() }
    }

    private fun initializeViews(view: View) {
        pointsTextView = view.findViewById(R.id.storePointsTextView)
        avatarRecyclerView = view.findViewById(R.id.avatarRecyclerView)
        categorySpinner = view.findViewById(R.id.categorySpinner)
        refreshButton = view.findViewById(R.id.refreshButton)
        avatarRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupCategoryFilter() {
        val categories = listOf("All", "Basic", "Premium", "Legendary", "Seasonal")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterAvatars(categories[position].lowercase())
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadData() {
        pointsTextView.text = "Loading..."
        loadUserPoints()
        loadUserProfile()
        allAvatars = avatarManager.getAllAvatars()
        updateAvatarList()
    }

    private fun loadUserPoints() {
        pointsManager.getCurrentUserPoints { userPoints ->
            currentUserPoints = userPoints?.totalPoints ?: 0
            pointsTextView.text = "Your Points: $currentUserPoints"
            updateAvatarList()
        }
    }

    private fun loadUserProfile() {
        avatarManager.ensureUserProfileExists { success ->
            if (success) {
                avatarManager.getUserProfile { profile ->
                    userProfile = profile
                    updateAvatarList()
                }
            } else {
                userProfile = UserProfile()
                updateAvatarList()
            }
        }
    }

    private fun filterAvatars(category: String) {
        val filteredAvatars = if (category == "all") allAvatars else allAvatars.filter { it.category == category }
        updateRecyclerView(filteredAvatars)
    }

    private fun updateAvatarList() {
        if (userProfile != null && allAvatars.isNotEmpty()) {
            val selectedCategory = categorySpinner.selectedItem?.toString()?.lowercase() ?: "all"
            filterAvatars(selectedCategory)
        }
    }

    private fun updateRecyclerView(avatars: List<Avatar>) {
        val profile = userProfile ?: return

        if (storeAdapter == null) {
            // Create adapter once
            storeAdapter = AvatarStoreAdapter(
                avatars = avatars,
                unlockedAvatars = profile.unlockedAvatars.toMutableList(),
                currentAvatar = profile.currentAvatar,
                userPoints = currentUserPoints,
                onPurchaseClick = { avatar -> purchaseAvatar(avatar) },
                onEquipClick = { avatar -> equipAvatar(avatar) }
            )
            avatarRecyclerView.adapter = storeAdapter
        } else {
            // Just refresh data
            storeAdapter?.updateData(
                newUnlocked = profile.unlockedAvatars,
                newCurrent = profile.currentAvatar,
                newPoints = currentUserPoints
            )
        }
    }

    private fun purchaseAvatar(avatar: Avatar) {
        avatarManager.purchaseAvatar(avatar) { success, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            if (success) {
                loadUserPoints()
                loadUserProfile()
            }
        }
    }

    private fun equipAvatar(avatar: Avatar) {
        avatarManager.setCurrentAvatar(avatar.id) { success, message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            if (success) {
                loadUserProfile()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
