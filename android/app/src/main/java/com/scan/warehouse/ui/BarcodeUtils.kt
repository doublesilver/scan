package com.scan.warehouse.ui

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
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
        }
        return spannable
    }
}
