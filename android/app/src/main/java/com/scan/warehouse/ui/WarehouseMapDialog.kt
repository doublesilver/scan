package com.scan.warehouse.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.scan.warehouse.R

object WarehouseMapDialog {

    fun show(context: Context, location: String?, onZoneClick: ((floor: Int, zone: String) -> Unit)? = null) {
        val parsed = parseLocation(location)
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(TextView(context).apply {
            text = "${parsed.floor}층 창고 도면"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * density).toInt())
        })

        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        var dialog: AlertDialog? = null

        val leftGrid = createGrid(context, listOf("A", "B", "C"), 3, parsed, parsed.floor, onZoneClick) { dialog?.dismiss() }
        val spacer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), 1)
        }
        val rightGrid = createGrid(context, listOf("D", "E", "F"), 3, parsed, parsed.floor, onZoneClick) { dialog?.dismiss() }

        gridContainer.addView(leftGrid)
        gridContainer.addView(spacer)
        gridContainer.addView(rightGrid)
        layout.addView(gridContainer)

        if (location != null) {
            layout.addView(TextView(context).apply {
                text = "현재 위치: $location"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, (16 * density).toInt(), 0, 0)
            })
        }

        dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("닫기", null)
            .show()
    }

    private data class ParsedLocation(val floor: Int, val zone: String, val shelf: String)

    private fun parseLocation(location: String?): ParsedLocation {
        if (location == null) return ParsedLocation(5, "", "")
        val parts = location.replace("층", "").split("-")
        return ParsedLocation(
            floor = parts.getOrNull(0)?.toIntOrNull() ?: 5,
            zone = parts.getOrNull(1) ?: "",
            shelf = parts.getOrNull(2) ?: ""
        )
    }

    private fun createGrid(
        context: Context,
        zones: List<String>,
        cols: Int,
        current: ParsedLocation,
        floor: Int,
        onZoneClick: ((floor: Int, zone: String) -> Unit)?,
        onDismiss: () -> Unit
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val cellSize = (48 * density).toInt()
        val margin = (3 * density).toInt()

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (zone in zones) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (col in 1..cols) {
                val shelf = String.format("%02d", col)
                val isCurrentCell = zone == current.zone && shelf == current.shelf

                val cell = TextView(context).apply {
                    text = "$zone-$shelf"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    typeface = if (isCurrentCell) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).apply {
                        setMargins(margin, margin, margin, margin)
                    }
                    if (isCurrentCell) {
                        setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                        setTextColor(Color.WHITE)
                    } else {
                        setBackgroundColor(ContextCompat.getColor(context, R.color.surface_container_high))
                        setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                    }
                    if (onZoneClick != null) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            onZoneClick.invoke(floor, zone)
                            onDismiss()
                        }
                    }
                }
                row.addView(cell)
            }
            grid.addView(row)
        }
        return grid
    }
}
