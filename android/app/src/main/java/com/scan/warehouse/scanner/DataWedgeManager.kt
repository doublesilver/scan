package com.scan.warehouse.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DataWedgeManager {

    const val ACTION_SCAN = "com.scan.warehouse.SCAN"
    const val EXTRA_DATA = "com.symbol.datawedge.data_string"

    private const val ACTION_DATAWEDGE = "com.symbol.datawedge.api.ACTION"
    private const val EXTRA_CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE"
    private const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"

    const val BARCODE_PATTERN = "^\\d{8,13}$"
    private val barcodeRegex = Regex(BARCODE_PATTERN)

    private var isRegistered = false

    private val _scanFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val scanFlow: SharedFlow<String> = _scanFlow.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SCAN) {
                val barcode = intent.getStringExtra(EXTRA_DATA) ?: return
                if (!barcode.matches(barcodeRegex)) return
                _scanFlow.tryEmit(barcode.trim())
            }
        }
    }

    fun register(context: Context) {
        if (isRegistered) unregister(context)
        val filter = IntentFilter(ACTION_SCAN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver, filter,
                "com.symbol.datawedge.permission.DATAWEDGE",
                null,
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, filter)
        }
        isRegistered = true
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
        isRegistered = false
    }

    fun setupProfile(context: Context) {
        Intent(ACTION_DATAWEDGE).also {
            it.putExtra(EXTRA_CREATE_PROFILE, "WarehouseScanner")
            context.sendBroadcast(it)
        }

        // 1. keystroke 끄기 (별도 Intent)
        val keystrokeConfig = Bundle().apply {
            putString("PROFILE_NAME", "WarehouseScanner")
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")
            val appConfig = Bundle().apply {
                putString("PACKAGE_NAME", context.packageName)
                putStringArray("ACTIVITY_LIST", arrayOf("*"))
            }
            putParcelableArray("APP_LIST", arrayOf(appConfig))
            val plugin = Bundle().apply {
                putString("PLUGIN_NAME", "KEYSTROKE")
                putString("RESET_CONFIG", "true")
                putBundle("PARAM_LIST", Bundle().apply {
                    putString("keystroke_output_enabled", "false")
                })
            }
            putBundle("PLUGIN_CONFIG", plugin)
        }
        Intent(ACTION_DATAWEDGE).also {
            it.putExtra(EXTRA_SET_CONFIG, keystrokeConfig)
            context.sendBroadcast(it)
        }

        // 2. intent 출력 설정 (별도 Intent)
        val intentConfig = Bundle().apply {
            putString("PROFILE_NAME", "WarehouseScanner")
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")
            val appConfig = Bundle().apply {
                putString("PACKAGE_NAME", context.packageName)
                putStringArray("ACTIVITY_LIST", arrayOf("*"))
            }
            putParcelableArray("APP_LIST", arrayOf(appConfig))
            val plugin = Bundle().apply {
                putString("PLUGIN_NAME", "INTENT")
                putString("RESET_CONFIG", "true")
                putBundle("PARAM_LIST", Bundle().apply {
                    putString("intent_output_enabled", "true")
                    putString("intent_action", ACTION_SCAN)
                    putString("intent_delivery", "2")
                })
            }
            putBundle("PLUGIN_CONFIG", plugin)
        }
        Intent(ACTION_DATAWEDGE).also {
            it.putExtra(EXTRA_SET_CONFIG, intentConfig)
            context.sendBroadcast(it)
        }
    }
}
