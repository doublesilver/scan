package com.scan.warehouse.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import coil.load
import com.google.gson.Gson
import com.scan.warehouse.R
import com.scan.warehouse.databinding.ActivityBoxDetailBinding
import com.scan.warehouse.model.BoxResponse
import com.scan.warehouse.model.FamilyMember

class BoxDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOX_DATA = "extra_box_data"

        fun createIntent(context: Context, boxJson: String): Intent {
            return Intent(context, BoxDetailActivity::class.java).apply {
                putExtra(EXTRA_BOX_DATA, boxJson)
            }
        }
    }

    private lateinit var binding: ActivityBoxDetailBinding
    private var memberCount = 0
    private var isSkuExpanded = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoxDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra(EXTRA_BOX_DATA) ?: run { finish(); return }
        val box = Gson().fromJson(json, BoxResponse::class.java)

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        if (box.productMasterImage != null) {
            binding.ivBoxImage.load(box.productMasterImage) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        }

        binding.tvProductMasterName.text = box.productMasterName
        binding.tvBoxName.text = box.boxName
        binding.tvBoxLocation.text = box.location ?: "-"

        memberCount = box.members.size
        isSkuExpanded = memberCount <= 4
        binding.layoutSkuTree.visibility = if (isSkuExpanded) View.VISIBLE else View.GONE
        updateSkuHeaderIcon(isSkuExpanded)

        binding.tvSkuHeader.setOnClickListener {
            isSkuExpanded = !isSkuExpanded
            binding.layoutSkuTree.visibility = if (isSkuExpanded) View.VISIBLE else View.GONE
            updateSkuHeaderIcon(isSkuExpanded)
        }

        box.members.forEachIndexed { index, member ->
            addSkuTreeItem(member, index == box.members.size - 1, index)
        }

        if (box.location != null) {
            binding.bottomBar.visibility = View.VISIBLE
            binding.btnBarMap.setOnClickListener {
                WarehouseMapDialog.show(this, box.location) { floor, zone ->
                    startActivity(ShelfListActivity.createIntent(this, floor, zone, box.location))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }
        }
    }

    private fun updateSkuHeaderIcon(expanded: Boolean) {
        val icon = if (expanded) "▼" else "▶"
        binding.tvSkuHeader.text = "$icon 하위 SKU ($memberCount)"
    }

    private fun addSkuTreeItem(member: FamilyMember, isLast: Boolean, index: Int = 0) {
        val density = resources.displayMetrics.density
        val pad = (14 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(
                this@BoxDetailActivity,
                if (index % 2 == 0) R.drawable.bg_info_row else R.drawable.bg_info_row_alt
            )
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = (6 * density).toInt()
            }
        }

        container.addView(TextView(this).apply {
            text = member.skuName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.primary))
        })

        container.addView(TextView(this).apply {
            text = "SKU: ${member.skuId}" + if (member.barcode != null) "  |  ${member.barcode}" else ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.on_surface_variant))
            typeface = Typeface.MONOSPACE
            setPadding(0, (4 * density).toInt(), 0, 0)
        })

        if (member.location != null) {
            container.addView(TextView(this).apply {
                text = "위치: ${member.location}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@BoxDetailActivity, R.color.on_surface))
                setPadding(0, (2 * density).toInt(), 0, 0)
            })
        }

        if (member.barcode != null) {
            container.isClickable = true
            container.isFocusable = true
            val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            container.foreground = attrs.getDrawable(0)
            attrs.recycle()
            container.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("BARCODE", member.barcode)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        binding.layoutSkuTree.addView(container)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
