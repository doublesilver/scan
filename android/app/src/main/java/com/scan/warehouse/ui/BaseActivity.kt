package com.scan.warehouse.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.scan.warehouse.R

abstract class BaseActivity : AppCompatActivity() {
    protected fun finishWithSlide() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    protected fun startWithSlide(intent: Intent) {
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}
