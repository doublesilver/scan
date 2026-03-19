package com.scan.warehouse.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ItemProductBinding
import com.scan.warehouse.model.SearchItem

class ProductAdapter(
    private val onItemClick: (SearchItem) -> Unit
) : ListAdapter<SearchItem, ProductAdapter.ProductViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchItem) {
            binding.tvItemProductName.text = item.productName
            binding.tvItemSkuId.text = item.skuId
            binding.tvItemCategory.text = item.category ?: ""
            binding.ivItemThumbnail.setImageResource(R.drawable.ic_placeholder)

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SearchItem>() {
            override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean {
                return oldItem.skuId == newItem.skuId
            }

            override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
