package student.projects.spotmap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AvatarStoreAdapter(
    private val avatars: List<Avatar>,
    private var unlockedAvatars: MutableList<String>,
    private var currentAvatar: String,
    private var userPoints: Int,
    private val onPurchaseClick: (Avatar) -> Unit,
    private val onEquipClick: (Avatar) -> Unit
) : RecyclerView.Adapter<AvatarStoreAdapter.AvatarViewHolder>() {

    class AvatarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarImageView: ImageView = itemView.findViewById(R.id.avatarImageView)
        val avatarNameTextView: TextView = itemView.findViewById(R.id.avatarNameTextView)
        val avatarPriceTextView: TextView = itemView.findViewById(R.id.avatarPriceTextView)
        val avatarCategoryTextView: TextView = itemView.findViewById(R.id.avatarCategoryTextView)
        val actionButton: Button = itemView.findViewById(R.id.actionButton)
        val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_avatar_store, parent, false)
        return AvatarViewHolder(view)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        val avatar = avatars[position]
        val avatarManager = AvatarManager()

        // Set avatar image
        val resourceId = avatarManager.getAvatarResourceId(avatar.id)
        holder.avatarImageView.setImageResource(resourceId)

        // Set avatar details
        holder.avatarNameTextView.text = avatar.name
        holder.avatarPriceTextView.text = "${avatar.price} Points"
        holder.avatarCategoryTextView.text = avatar.category.replaceFirstChar { it.uppercase() }

        // Status
        val isUnlocked = unlockedAvatars.contains(avatar.id)
        val isCurrent = currentAvatar == avatar.id
        val canAfford = userPoints >= avatar.price

        when {
            isCurrent -> {
                holder.statusTextView.text = "EQUIPPED"
                holder.statusTextView.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
                holder.actionButton.text = "EQUIPPED"
                holder.actionButton.isEnabled = false
                holder.actionButton.alpha = 0.6f
            }
            isUnlocked -> {
                holder.statusTextView.text = "OWNED"
                holder.statusTextView.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
                holder.actionButton.text = "EQUIP"
                holder.actionButton.isEnabled = true
                holder.actionButton.alpha = 1.0f
                holder.actionButton.setOnClickListener {
                    currentAvatar = avatar.id
                    onEquipClick(avatar)
                    notifyDataSetChanged()
                }
            }
            canAfford -> {
                holder.statusTextView.text = "AVAILABLE"
                holder.statusTextView.setTextColor(holder.itemView.context.getColor(android.R.color.black))
                holder.actionButton.text = "BUY"
                holder.actionButton.isEnabled = true
                holder.actionButton.alpha = 1.0f
                holder.actionButton.setOnClickListener {
                    unlockedAvatars.add(avatar.id)
                    userPoints -= avatar.price
                    onPurchaseClick(avatar)
                    notifyDataSetChanged()
                }
            }
            else -> {
                holder.statusTextView.text = "INSUFFICIENT POINTS"
                holder.statusTextView.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
                holder.actionButton.text = "BUY"
                holder.actionButton.isEnabled = false
                holder.actionButton.alpha = 0.6f
            }
        }
    }

    override fun getItemCount(): Int = avatars.size

    // 🔑 Call this when the fragment/activity updates data
    fun updateData(newUnlocked: List<String>, newCurrent: String, newPoints: Int) {
        this.unlockedAvatars = newUnlocked.toMutableList()
        this.currentAvatar = newCurrent
        this.userPoints = newPoints
        notifyDataSetChanged()
    }
}
