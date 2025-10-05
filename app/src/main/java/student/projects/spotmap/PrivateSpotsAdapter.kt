package student.projects.spotmap.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import student.projects.spotmap.R

class PrivateSpotsAdapter(
    private val spots: List<Map<*, *>>,
    private val onMakePublic: (String, Map<*, *>) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<PrivateSpotsAdapter.PrivateSpotViewHolder>() {

    inner class PrivateSpotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSpotName)
        val tvDesc: TextView = view.findViewById(R.id.tvSpotDesc)
        val btnMakePublic: Button = view.findViewById(R.id.btnMakePublic)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrivateSpotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_private_spot, parent, false)
        return PrivateSpotViewHolder(view)
    }

    override fun onBindViewHolder(holder: PrivateSpotViewHolder, position: Int) {
        val spot = spots[position]
        val name = spot["name"]?.toString() ?: "Unnamed Spot"
        val desc = spot["description"]?.toString() ?: "No description"
        val id = spot["id"]?.toString() ?: ""

        holder.tvName.text = name
        holder.tvDesc.text = desc

        holder.btnMakePublic.setOnClickListener {
            onMakePublic(id, spot)
        }

        holder.btnDelete.setOnClickListener {
            onDelete(id)
        }
    }

    override fun getItemCount(): Int = spots.size
}
