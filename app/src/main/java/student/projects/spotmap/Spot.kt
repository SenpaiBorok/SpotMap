package student.projects.spotmap

data class Spot(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val description: String = "",
    val vibeTags: List<String> = emptyList(),
    val isPublic: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val images: List<String> = emptyList(),
    val userId: String = "",
    val timestamp: Long = 0L,
    val comments: List<Comment> = emptyList(),
    val isApproved: Boolean = false,
    val avgRating: Int = 0,
    val totalReviews: Int = 0
)

data class Comment(
    val userId: String = "",
    val username: String = "",
    val message: String = "",
    val createdAt: String = ""
)
