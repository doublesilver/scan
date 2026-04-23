package com.scan.warehouse.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.scan.warehouse.R

object CellStyleUtil {
    fun applyStyle(cell: TextView, status: String, context: Context) {
        cell.gravity = Gravity.CENTER
        when (status) {
            "used" -> {
                cell.setBackgroundColor(ContextCompat.getColor(context, R.color.cell_used_light))
                cell.setTextColor(ContextCompat.getColor(context, R.color.cell_used_dark))
            }
            "full" -> {
                cell.setBackgroundColor(ContextCompat.getColor(context, R.color.cell_full_light))
                cell.setTextColor(ContextCompat.getColor(context, R.color.cell_full_dark))
            }
            "aisle" -> {
                cell.setBackgroundColor(Color.parseColor("#2a2a2a"))
                cell.setTextColor(Color.parseColor("#999999"))
                cell.typeface = Typeface.DEFAULT
            }
            "table" -> {
                cell.setBackgroundColor(Color.parseColor("#3d2e1e"))
                cell.setTextColor(Color.parseColor("#d4a574"))
                cell.typeface = Typeface.DEFAULT_BOLD
            }
            "pc" -> {
                cell.setBackgroundColor(Color.parseColor("#1e3a5f"))
                cell.setTextColor(Color.parseColor("#9fc5e8"))
                cell.typeface = Typeface.DEFAULT_BOLD
            }
            else -> {
                cell.setBackgroundColor(Color.TRANSPARENT)
                cell.text = ""
            }
        }
    }
}
