package student.projects.spotmap

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import student.projects.spotmap.PointsManager
import student.projects.spotmap.R

data class Avatar(
    val id: String = "",
    val name: String = "",
    val price: Int = 0,
    val category: String = "",
    val resourceId: Int = 0,
    val description: String = ""
)

data class UserProfile(
    val currentAvatar: String = "default_avatar",
    val unlockedAvatars: List<String> = listOf("default_avatar"),
    val displayName: String = ""
)

class AvatarManager {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val pointsManager = PointsManager()

    companion object {
        // Avatar categories
        const val CATEGORY_BASIC = "basic"
        const val CATEGORY_PREMIUM = "premium"
        const val CATEGORY_LEGENDARY = "legendary"
        const val CATEGORY_SEASONAL = "seasonal"

        // Default avatar
        const val DEFAULT_AVATAR = "default_avatar"
    }

    fun getAllAvatars(): List<Avatar> {
        return listOf(
            // Default Avatar (Free)
            Avatar("default_avatar", "Default Avatar", 0, CATEGORY_BASIC, R.drawable.default_avatar, "Your starting companion"),

            // Basic Avatars (50-100 points)
            Avatar("cat_basic", "Cute Cat", 50, CATEGORY_BASIC, R.drawable.avatar_cat, "A friendly feline companion"),
            Avatar("dog_basic", "Happy Dog", 50, CATEGORY_BASIC, R.drawable.avatar_dog, "Man's best friend"),
            Avatar("rabbit_basic", "Bouncy Rabbit", 75, CATEGORY_BASIC, R.drawable.avatar_rabbit, "Hop into savings!"),
            Avatar("bird_basic", "Chirpy Bird", 75, CATEGORY_BASIC, R.drawable.avatar_bird, "Tweet your way to success"),
            Avatar("fish_basic", "Swimming Fish", 100, CATEGORY_BASIC, R.drawable.avatar_fish, "Go with the flow"),

            // Premium Avatars (150-250 points)
            Avatar("fox_premium", "Clever Fox", 150, CATEGORY_PREMIUM, R.drawable.avatar_fox, "Outsmart overspending"),
            Avatar("lion_premium", "Majestic Lion", 200, CATEGORY_PREMIUM, R.drawable.avatar_lion, "King of budget management"),
            Avatar("eagle_premium", "Soaring Eagle", 200, CATEGORY_PREMIUM, R.drawable.avatar_eagle, "Rise above your expenses"),
            Avatar("bear_premium", "Strong Bear", 250, CATEGORY_PREMIUM, R.drawable.avatar_bear, "Bear-y good with money"),
            Avatar("wolf_premium", "Wise Wolf", 250, CATEGORY_PREMIUM, R.drawable.avatar_wolf, "Hunt down those deals"),

            // Legendary Avatars (400-500 points)
            Avatar("unicorn_legendary", "Magic Unicorn", 400, CATEGORY_LEGENDARY, R.drawable.avatar_unicorn, "Magical money management"),
            Avatar("phoenix_legendary", "Fire Phoenix", 450, CATEGORY_LEGENDARY, R.drawable.avatar_phoenix, "Rise from debt ashes"),
            Avatar("dragon_legendary", "Golden Dragon", 500, CATEGORY_LEGENDARY, R.drawable.avatar_dragon, "Legendary wealth guardian"),

            // Seasonal Avatars (100-300 points)
            Avatar("tree_seasonal", "Spring Tree", 100, CATEGORY_SEASONAL, R.drawable.avatar_tree, "Grow your savings"),
            Avatar("snowman_seasonal", "Winter Snowman", 150, CATEGORY_SEASONAL, R.drawable.avatar_snowman, "Cool savings ahead"),
            Avatar("pumpkin_seasonal", "Halloween Pumpkin", 200, CATEGORY_SEASONAL, R.drawable.avatar_pumpkin, "Spook-tacular savings")
        )
    }

    fun getUserProfile(callback: (UserProfile?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(null)

        database.getReference("users").child(uid).child("profile")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val profile = snapshot.getValue(UserProfile::class.java) ?: UserProfile()
                    callback(profile)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(null)
                }
            })
    }

    fun createDefaultProfile(callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false)

        val defaultProfile = UserProfile(
            currentAvatar = DEFAULT_AVATAR,
            unlockedAvatars = listOf(DEFAULT_AVATAR),
            displayName = auth.currentUser?.email?.substringBefore("@") ?: "User"
        )

        database.getReference("users").child(uid).child("profile")
            .setValue(defaultProfile)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun purchaseAvatar(avatar: Avatar, callback: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false, "User not logged in")

        // Check if it's the default avatar (should be free)
        if (avatar.id == DEFAULT_AVATAR) {
            return callback(false, "Default avatar is already unlocked!")
        }

        // Check if user has enough points
        pointsManager.getCurrentUserPoints { userPoints ->
            if (userPoints == null) return@getCurrentUserPoints callback(false, "Failed to load points")

            if (userPoints.totalPoints < avatar.price) {
                return@getCurrentUserPoints callback(false, "Insufficient points! You need ${avatar.price} points.")
            }

            // Check if already owned
            getUserProfile { profile ->
                if (profile == null) return@getUserProfile callback(false, "Failed to load profile")

                if (profile.unlockedAvatars.contains(avatar.id)) {
                    return@getUserProfile callback(false, "You already own this avatar!")
                }

                // Deduct points
                pointsManager.spendPoints(avatar.price, "Purchased ${avatar.name}") { success, message ->
                    if (!success) return@spendPoints callback(false, message)

                    // Add avatar to unlocked list
                    val updatedAvatars = profile.unlockedAvatars.toMutableList()
                    updatedAvatars.add(avatar.id)
                    val updatedProfile = profile.copy(unlockedAvatars = updatedAvatars)

                    // Save updated profile
                    database.getReference("users").child(uid).child("profile")
                        .setValue(updatedProfile)
                        .addOnSuccessListener {
                            callback(true, "Successfully purchased ${avatar.name}!")
                        }
                        .addOnFailureListener {
                            callback(false, "Failed to save purchase")
                        }
                }
            }
        }
    }

    fun setCurrentAvatar(avatarId: String, callback: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false, "User not logged in")

        getUserProfile { profile ->
            if (profile == null) return@getUserProfile callback(false, "Failed to load profile")

            if (!profile.unlockedAvatars.contains(avatarId)) {
                return@getUserProfile callback(false, "You don't own this avatar!")
            }

            if (profile.currentAvatar == avatarId) {
                return@getUserProfile callback(false, "This avatar is already equipped!")
            }

            val updatedProfile = profile.copy(currentAvatar = avatarId)

            database.getReference("users").child(uid).child("profile")
                .setValue(updatedProfile)
                .addOnSuccessListener {
                    val avatarName = getAvatarById(avatarId)?.name ?: "Avatar"
                    callback(true, "$avatarName equipped successfully!")
                }
                .addOnFailureListener {
                    callback(false, "Failed to update avatar")
                }
        }
    }

    fun getAvatarResourceId(avatarId: String): Int {
        return when (avatarId) {
            "default_avatar" -> R.drawable.default_avatar
            "cat_basic" -> R.drawable.avatar_cat
            "dog_basic" -> R.drawable.avatar_dog
            "rabbit_basic" -> R.drawable.avatar_rabbit
            "bird_basic" -> R.drawable.avatar_bird
            "fish_basic" -> R.drawable.avatar_fish
            "fox_premium" -> R.drawable.avatar_fox
            "lion_premium" -> R.drawable.avatar_lion
            "eagle_premium" -> R.drawable.avatar_eagle
            "bear_premium" -> R.drawable.avatar_bear
            "wolf_premium" -> R.drawable.avatar_wolf
            "unicorn_legendary" -> R.drawable.avatar_unicorn
            "phoenix_legendary" -> R.drawable.avatar_phoenix
            "dragon_legendary" -> R.drawable.avatar_dragon
            "tree_seasonal" -> R.drawable.avatar_tree
            "snowman_seasonal" -> R.drawable.avatar_snowman
            "pumpkin_seasonal" -> R.drawable.avatar_pumpkin
            else -> R.drawable.ic_person // Fallback
        }
    }

    fun getAvatarById(avatarId: String): Avatar? {
        return getAllAvatars().find { it.id == avatarId }
    }

    fun getAvatarsByCategory(category: String): List<Avatar> {
        return getAllAvatars().filter { it.category.equals(category, ignoreCase = true) }
    }

    fun isAvatarUnlocked(avatarId: String, userProfile: UserProfile): Boolean {
        return userProfile.unlockedAvatars.contains(avatarId)
    }

    fun isCurrentAvatar(avatarId: String, userProfile: UserProfile): Boolean {
        return userProfile.currentAvatar == avatarId
    }

    fun getUnlockedAvatarsCount(userProfile: UserProfile): Int {
        return userProfile.unlockedAvatars.size
    }

    fun getTotalAvatarsCount(): Int {
        return getAllAvatars().size
    }

    fun getProgressPercentage(userProfile: UserProfile): Int {
        val unlocked = getUnlockedAvatarsCount(userProfile)
        val total = getTotalAvatarsCount()
        return if (total > 0) (unlocked * 100) / total else 0
    }

    fun ensureUserProfileExists(callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false)

        database.getReference("users").child(uid).child("profile")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        createDefaultProfile(callback)
                    } else {
                        callback(true)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(false)
                }
            })
    }

    fun updateDisplayName(newName: String, callback: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback(false, "User not logged in")

        getUserProfile { profile ->
            if (profile == null) return@getUserProfile callback(false, "Failed to load profile")

            val updatedProfile = profile.copy(displayName = newName)

            database.getReference("users").child(uid).child("profile")
                .setValue(updatedProfile)
                .addOnSuccessListener {
                    callback(true, "Display name updated successfully!")
                }
                .addOnFailureListener {
                    callback(false, "Failed to update display name")
                }
        }
    }

    fun getAvatarStats(callback: (Map<String, Int>) -> Unit) {
        val allAvatars = getAllAvatars()
        val stats = mutableMapOf<String, Int>()

        // Count avatars by category
        stats["total"] = allAvatars.size
        stats[CATEGORY_BASIC] = allAvatars.count { it.category == CATEGORY_BASIC }
        stats[CATEGORY_PREMIUM] = allAvatars.count { it.category == CATEGORY_PREMIUM }
        stats[CATEGORY_LEGENDARY] = allAvatars.count { it.category == CATEGORY_LEGENDARY }
        stats[CATEGORY_SEASONAL] = allAvatars.count { it.category == CATEGORY_SEASONAL }

        callback(stats)
    }
}
