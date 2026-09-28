package com.vidhya.focuslock

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class InstalledApp(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable?)

class AppListAdapter(
    private val apps: List<InstalledApp>,
    private val isChecked: (String) -> Boolean,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val checkbox: CheckBox = view.findViewById(R.id.appCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.name.text = app.label
        holder.icon.setImageDrawable(app.icon)
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = isChecked(app.packageName)
        holder.checkbox.setOnCheckedChangeListener { _, checked ->
            onToggle(app.packageName, checked)
        }
    }

    override fun getItemCount() = apps.size

    companion object {
        fun loadInstalledApps(pm: PackageManager): List<InstalledApp> {
            return pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { InstalledApp(pm.getApplicationLabel(it).toString(), it.packageName, pm.getApplicationIcon(it)) }
                .sortedBy { it.label.lowercase() }
        }
    }
}
