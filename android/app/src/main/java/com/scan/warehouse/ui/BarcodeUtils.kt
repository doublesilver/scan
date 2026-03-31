package com.scan.warehouse.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

object BarcodeUtils {

    fun formatBold(barcode: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(barcode)
        if (barcode.length >= 5) {
            val start = barcode.length - 5
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start, barcode.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                RelativeSizeSpan(1.3f),
                start, barcode.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#FF3B30")),
                start, barcode.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    private val COLOR_KEYWORDS = listOf(
        "블랙" to "#555555",
        "화이트" to "#EEEEEE",
        "핑크" to "#FF69B4",
        "블루" to "#4A90D9",
        "레드" to "#FF3B30",
        "그린" to "#34C759",
        "옐로우" to "#FFD60A",
        "옐로" to "#FFD60A",
        "퍼플" to "#AF52DE",
        "오렌지" to "#FF9500",
        "그레이" to "#8E8E93",
        "브라운" to "#A0522D",
        "네이비" to "#3B5998",
        "베이지" to "#D4C5A9",
        "실버" to "#C0C0C0",
        "골드" to "#FFD700",
        "카키" to "#BDB76B",
        "BLACK" to "#555555",
        "WHITE" to "#EEEEEE",
        "PINK" to "#FF69B4",
        "BLUE" to "#4A90D9",
        "RED" to "#FF3B30",
        "GREEN" to "#34C759",
        "YELLOW" to "#FFD60A",
        "PURPLE" to "#AF52DE",
        "ORANGE" to "#FF9500",
        "GRAY" to "#8E8E93",
        "GREY" to "#8E8E93",
        "BROWN" to "#A0522D",
        "NAVY" to "#3B5998",
        "BEIGE" to "#D4C5A9",
        "SILVER" to "#C0C0C0",
        "GOLD" to "#FFD700",
        "KHAKI" to "#BDB76B",
    ).sortedByDescending { it.first.length }

    fun applyColorKeywords(text: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(text)
        val matched = BooleanArray(text.length)
        for ((keyword, hex) in COLOR_KEYWORDS) {
            var startIndex = 0
            while (true) {
                val index = text.indexOf(keyword, startIndex, ignoreCase = true)
                if (index < 0) break
                val end = index + keyword.length
                if ((index until end).none { matched[it] }) {
                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor(hex)),
                        index, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        index, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    for (i in index until end) matched[i] = true
                }
                startIndex = end
            }
        }
        return spannable
    }
}
