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
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapZone

object WarehouseMapDialog {

    private val FALLBACK_ZONES = listOf(
        MapZone("A", "501호", 3, 4),
        MapZone("B", "포장다이", 3, 2),
        MapZone("C", "502호", 3, 3),
    )

    fun show(
        context: Context,
        location: String?,
        mapLayout: MapLayout? = null,
        onCellClick: ((floor: Int, zone: String, row: Int, col: Int, cellKey: String) -> Unit)? = null
    ) {
        val parsed = parseLocation(location)
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val zones = if (mapLayout != null && mapLayout.zones.isNotEmpty()) mapLayout.zones else FALLBACK_ZONES
        val floor = mapLayout?.floor ?: parsed.floor

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(TextView(context).apply {
            text = "${floor}층 창고 도면"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        var dialog: AlertDialog? = null

        for (zone in zones) {
            layout.addView(TextView(context).apply {
                text = "${zone.name} (${zone.code}구역)"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
            })

            val grid = createGrid(context, zone, mapLayout, parsed, floor, onCellClick) { dialog?.dismiss() }
            layout.addView(grid)
        }

        if (location != null) {
            layout.addView(TextView(context).apply {
                text = "현재 위치: $location"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, (12 * density).toInt(), 0, 0)
            })
        }

        val scrollView = android.widget.ScrollView(context).apply { addView(layout) }

        dialog = AlertDialog.Builder(context)
            .setView(scrollView)
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
        zone: MapZone,
        mapLayout: MapLayout?,
        current: ParsedLocation,
        floor: Int,
        onCellClick: ((floor: Int, zone: String, row: Int, col: Int, cellKey: String) -> Unit)?,
        onDismiss: () -> Unit
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val cellSize = (48 * density).toInt()
        val margin = (3 * density).toInt()

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (row in 1..zone.rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (col in 1..zone.cols) {
                val cellNum = (row - 1) * zone.cols + col
                val shelf = String.format("%02d", cellNum)
                val isCurrentCell = zone.code == current.zone && shelf == current.shelf

                val cellKey = "${zone.code}-$row-$col"
                val cellData = mapLayout?.cells?.get(cellKey)
                val cellText = cellData?.name ?: "$row-$col"

                val cell = TextView(context).apply {
                    text = cellText
                    textSize = 10f
                    gravity = Gravity.CENTER
                    typeface = if (isCurrentCell) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).apply {
                        setMargins(margin, margin, margin, margin)
                    }
                    when {
                        isCurrentCell -> {
                            setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                            setTextColor(Color.WHITE)
                        }
                        cellData?.status == "full" -> {
                            setBackgroundColor(Color.parseColor("#FFCDD2"))
                            setTextColor(Color.parseColor("#B71C1C"))
                        }
                        cellData?.status == "used" -> {
                            setBackgroundColor(Color.parseColor("#C8E6C9"))
                            setTextColor(Color.parseColor("#1B5E20"))
                        }
                        else -> {
                            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_container_high))
                            setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                        }
                    }
                    if (onCellClick != null) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            onCellClick.invoke(floor, zone.code, row, col, cellKey)
                            onDismiss()
                        }
                    }
                }
                rowLayout.addView(cell)
            }
            grid.addView(rowLayout)
        }
        return grid
    }
}
