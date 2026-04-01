package com.scan.warehouse.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.model.ShelfItem

class ShelfAdapter(
    private val currentShelfNumber: Int?,
    private val onEditLabel: (ShelfItem) -> Unit,
    private val onCamera: (ShelfItem) -> Unit,
    private val onDeletePhoto: (ShelfItem) -> Unit
) : ListAdapter<ShelfItem, ShelfAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ShelfItem>() {
            override fun areItemsTheSame(a: ShelfItem, b: ShelfItem) = a.id == b.id
            override fun areContentsTheSame(a: ShelfItem, b: ShelfItem) = a == b
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.ivShelfPhoto)
        val tvNumber: TextView = itemView.findViewById(R.id.tvShelfNumber)
        val tvLabel: TextView = itemView.findViewById(R.id.tvShelfLabel)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditLabel)
        val btnCamera: ImageButton = itemView.findViewById(R.id.btnCamera)
        val btnDeletePhoto: ImageButton = itemView.findViewById(R.id.btnDeletePhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shelf, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val isHighlighted = currentShelfNumber != null && item.shelfNumber == currentShelfNumber

        holder.itemView.setBackgroundColor(
            if (isHighlighted)
                ContextCompat.getColor(context, R.color.primary).let {
                    android.graphics.Color.argb(40, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
                }
            else
                ContextCompat.getColor(context, R.color.surface_container_low)
        )

        holder.tvNumber.text = context.getString(R.string.shelf_number_format, item.shelfNumber)

        if (item.label != null) {
            holder.tvLabel.visibility = View.VISIBLE
            holder.tvLabel.text = item.label
        } else {
            holder.tvLabel.visibility = View.GONE
        }

        if (item.photoUrl != null) {
            val fullUrl = if (item.photoUrl.startsWith("http")) item.photoUrl
            else {
                val base = com.scan.warehouse.network.RetrofitClient.getBaseUrl(holder.itemView.context)
                val b = if (base.endsWith("/")) base.dropLast(1) else base
                "$b${item.photoUrl}"
            }
            holder.ivPhoto.load(fullUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
            holder.btnDeletePhoto.visibility = View.VISIBLE
        } else {
            holder.ivPhoto.setImageResource(R.drawable.ic_placeholder)
            holder.btnDeletePhoto.visibility = View.GONE
        }

        holder.btnEdit.setOnClickListener { onEditLabel(item) }
        holder.btnCamera.setOnClickListener { onCamera(item) }
        holder.btnDeletePhoto.setOnClickListener { onDeletePhoto(item) }
    }
}
