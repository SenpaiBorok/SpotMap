package student.projects.spotmap.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import student.projects.spotmap.R

class SettingsAdapter(
    private val options: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    inner class SettingsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvOption: TextView = view.findViewById(R.id.tvSettingOption)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting_option, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        val option = options[position]
        holder.tvOption.text = option
        holder.itemView.setOnClickListener { onClick(option) }
    }

    override fun getItemCount(): Int = options.size
}
