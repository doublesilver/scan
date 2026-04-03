package com.scan.warehouse.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.scan.warehouse.R
import com.scan.warehouse.model.MapLayout
import com.scan.warehouse.model.MapZone
import com.scan.warehouse.model.ParsedLocation
import com.scan.warehouse.model.Zone
import com.scan.warehouse.repository.ProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        repository: ProductRepository? = null,
        onRefresh: (() -> Unit)? = null,
        onCellClick: ((floor: Int, zone: String, row: Int, col: Int, cellKey: String) -> Unit)? = null
    ) {
        val parsed = ParsedLocation.parse(location)
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val zones = if (mapLayout != null && mapLayout.zones.isNotEmpty()) mapLayout.zones else FALLBACK_ZONES
        val floor = mapLayout?.floor ?: parsed.floor

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        headerRow.addView(TextView(context).apply {
            text = "${floor}층 창고 도면"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (repository != null) {
            headerRow.addView(TextView(context).apply {
                text = "+ 구역"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                val bg = GradientDrawable().apply {
                    setStroke((1 * density).toInt(), ContextCompat.getColor(context, R.color.primary))
                    cornerRadius = 4 * density
                }
                background = bg
                setOnClickListener { showZoneCreateDialog(context, repository, onRefresh) }
            })
        }
        layout.addView(headerRow)

        var dialog: AlertDialog? = null
        val animators = mutableListOf<ObjectAnimator>()

        for (zone in zones) {
            val zoneHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
            }
            zoneHeader.addView(TextView(context).apply {
                text = "${zone.name} (${zone.code}구역)"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (repository != null) {
                zoneHeader.addView(TextView(context).apply {
                    text = "✏"
                    textSize = 16f
                    setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                    setOnClickListener {
                        showZoneEditDialog(context, zone, repository, onRefresh)
                    }
                })
            }
            layout.addView(zoneHeader)
            val grid = createGrid(context, zone, mapLayout, parsed, floor, false, animators, onCellClick) { dialog?.dismiss() }
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
            .create()

        dialog!!.setOnDismissListener {
            animators.forEach { it.cancel() }
            animators.clear()
        }
        dialog!!.show()
    }

    private fun createGrid(
        context: Context,
        zone: MapZone,
        mapLayout: MapLayout?,
        current: ParsedLocation,
        floor: Int,
        landscape: Boolean = false,
        animators: MutableList<ObjectAnimator>,
        onCellClick: ((floor: Int, zone: String, row: Int, col: Int, cellKey: String) -> Unit)?,
        onDismiss: () -> Unit
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val cellSize = (48 * density).toInt()
        val margin = (3 * density).toInt()

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val outerRange = if (landscape) 1..zone.cols else 1..zone.rows
        val innerRange = if (landscape) 1..zone.rows else 1..zone.cols

        for (outer in outerRange) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (inner in innerRange) {
                val row = if (landscape) inner else outer
                val col = if (landscape) outer else inner
                val cellNum = (row - 1) * zone.cols + col
                val shelf = cellNum.toString()
                val isCurrentCell = zone.code == current.zone && shelf == current.shelf

                val cellKey = "${zone.code}-$row-$col"
                val cellData = mapLayout?.cells?.get(cellKey)
                val cellText = cellData?.name ?: "${zone.code}-$cellNum"

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
                            val gd = GradientDrawable().apply {
                                setColor(ContextCompat.getColor(context, R.color.cell_highlight))
                                setStroke(3, ContextCompat.getColor(context, R.color.cell_highlight_stroke))
                                cornerRadius = 6f
                            }
                            background = gd
                            setTextColor(Color.BLACK)
                        }
                        cellData?.status == "full" -> {
                            setBackgroundColor(ContextCompat.getColor(context, R.color.cell_full_light))
                            setTextColor(ContextCompat.getColor(context, R.color.cell_full_dark))
                        }
                        cellData?.status == "used" -> {
                            setBackgroundColor(ContextCompat.getColor(context, R.color.cell_used_light))
                            setTextColor(ContextCompat.getColor(context, R.color.cell_used_dark))
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
                if (isCurrentCell) {
                    ObjectAnimator.ofFloat(cell, "alpha", 1f, 0.3f).apply {
                        duration = 600
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        start()
                        animators.add(this)
                    }
                }
                rowLayout.addView(cell)
            }
            grid.addView(rowLayout)
        }
        return grid
    }

    private fun showZoneEditDialog(context: Context, zone: MapZone, repository: ProductRepository, onRefresh: (() -> Unit)?) {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val nameEdit = EditText(context).apply { setText(zone.name); hint = "이름" }
        val codeEdit = EditText(context).apply { setText(zone.code); hint = "코드" }
        val rowsEdit = EditText(context).apply { setText(zone.rows.toString()); hint = "행"; inputType = InputType.TYPE_CLASS_NUMBER }
        val colsEdit = EditText(context).apply { setText(zone.cols.toString()); hint = "열"; inputType = InputType.TYPE_CLASS_NUMBER }

        fun addField(label: String, edit: EditText) {
            layout.addView(TextView(context).apply { text = label; textSize = 13f; setPadding(0, (8 * density).toInt(), 0, (2 * density).toInt()) })
            layout.addView(edit)
        }
        addField("이름", nameEdit)
        addField("코드", codeEdit)
        addField("행", rowsEdit)
        addField("열", colsEdit)

        val zoneIdFromCode = zone.code.toIntOrNull()

        AlertDialog.Builder(context)
            .setTitle("구역 설정")
            .setView(layout)
            .setPositiveButton("저장") { _, _ ->
                val data = mutableMapOf<String, Any>()
                val newName = nameEdit.text.toString().trim()
                val newCode = codeEdit.text.toString().trim()
                val newRows = rowsEdit.text.toString().toIntOrNull() ?: zone.rows
                val newCols = colsEdit.text.toString().toIntOrNull() ?: zone.cols
                if (newName.isNotEmpty()) data["name"] = newName
                if (newCode.isNotEmpty()) data["code"] = newCode
                data["rows"] = newRows
                data["cols"] = newCols

                if (zoneIdFromCode != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = repository.updateZone(zoneIdFromCode, data)
                        if (result.isSuccess) {
                            Toast.makeText(context, "구역 수정 완료", Toast.LENGTH_SHORT).show()
                            onRefresh?.invoke()
                        } else {
                            Toast.makeText(context, "수정 실패: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .setNeutralButton("삭제") { _, _ ->
                if (zoneIdFromCode != null) {
                    AlertDialog.Builder(context)
                        .setTitle("구역 삭제")
                        .setMessage("'${zone.name}' 구역을 삭제하시겠습니까?\n하위 셀/층/상품이 모두 삭제됩니다.")
                        .setPositiveButton("삭제") { _, _ ->
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = repository.deleteZone(zoneIdFromCode)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "구역 삭제 완료", Toast.LENGTH_SHORT).show()
                                    onRefresh?.invoke()
                                } else {
                                    Toast.makeText(context, "삭제 실패: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }
            .show()
    }

    private fun showZoneCreateDialog(context: Context, repository: ProductRepository, onRefresh: (() -> Unit)?) {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val nameEdit = EditText(context).apply { hint = "이름 (예: 501호)" }
        val codeEdit = EditText(context).apply { hint = "코드 (예: 4)" }
        val rowsEdit = EditText(context).apply { hint = "행"; inputType = InputType.TYPE_CLASS_NUMBER; setText("3") }
        val colsEdit = EditText(context).apply { hint = "열"; inputType = InputType.TYPE_CLASS_NUMBER; setText("4") }

        fun addField(label: String, edit: EditText) {
            layout.addView(TextView(context).apply { text = label; textSize = 13f; setPadding(0, (8 * density).toInt(), 0, (2 * density).toInt()) })
            layout.addView(edit)
        }
        addField("이름", nameEdit)
        addField("코드", codeEdit)
        addField("행", rowsEdit)
        addField("열", colsEdit)

        AlertDialog.Builder(context)
            .setTitle("새 구역 추가")
            .setView(layout)
            .setPositiveButton("추가") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val code = codeEdit.text.toString().trim()
                val rows = rowsEdit.text.toString().toIntOrNull() ?: 3
                val cols = colsEdit.text.toString().toIntOrNull() ?: 4

                if (name.isEmpty() || code.isEmpty()) {
                    Toast.makeText(context, "이름과 코드를 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val data = mapOf<String, Any>("name" to name, "code" to code, "rows" to rows, "cols" to cols)
                CoroutineScope(Dispatchers.Main).launch {
                    val result = repository.createZone(data)
                    if (result.isSuccess) {
                        Toast.makeText(context, "구역 추가 완료", Toast.LENGTH_SHORT).show()
                        onRefresh?.invoke()
                    } else {
                        Toast.makeText(context, "추가 실패: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
