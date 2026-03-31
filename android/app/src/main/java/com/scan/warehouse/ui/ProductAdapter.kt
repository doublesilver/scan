package com.scan.warehouse.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ItemProductBinding
import com.scan.warehouse.model.SearchItem
import com.scan.warehouse.repository.ProductRepository

class ProductAdapter(
    private val repository: ProductRepository,
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

            val imageUrl = item.thumbnail?.let { repository.getImageUrl(it) }
            if (imageUrl != null) {
                binding.ivItemImage.load(imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                    error(R.drawable.ic_placeholder)
                }
            } else {
                binding.ivItemImage.setImageResource(R.drawable.ic_placeholder)
            }

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
