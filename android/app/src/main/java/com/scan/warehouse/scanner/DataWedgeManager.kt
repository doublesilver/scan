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
import kotlinx.coroutines.flow.distinctUntilChanged

object DataWedgeManager {

    const val ACTION_SCAN = "com.scan.warehouse.SCAN"
    const val EXTRA_DATA = "com.symbol.datawedge.data_string"

    private const val ACTION_DATAWEDGE = "com.symbol.datawedge.api.ACTION"
    private const val EXTRA_CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE"
    private const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"

    const val BARCODE_PATTERN = "^\\d{8,13}$"
    private val barcodeRegex = Regex(BARCODE_PATTERN)

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
        val filter = IntentFilter(ACTION_SCAN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun setupProfile(context: Context) {
        Intent(ACTION_DATAWEDGE).also { intent ->
            intent.putExtra(EXTRA_CREATE_PROFILE, "WarehouseScanner")
            context.sendBroadcast(intent)
        }

        val profileConfig = Bundle().apply {
            putString("PROFILE_NAME", "WarehouseScanner")
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")

            val appConfig = Bundle().apply {
                putString("PACKAGE_NAME", context.packageName)
                putStringArray("ACTIVITY_LIST", arrayOf("*"))
            }
            putParcelableArray("APP_LIST", arrayOf(appConfig))

            val intentPlugin = Bundle().apply {
                putString("PLUGIN_NAME", "INTENT")
                putString("RESET_CONFIG", "true")
                val intentParams = Bundle().apply {
                    putString("intent_output_enabled", "true")
                    putString("intent_action", ACTION_SCAN)
                    putString("intent_delivery", "2")
                }
                putBundle("PARAM_LIST", intentParams)
            }
            putParcelableArray("PLUGIN_CONFIG", arrayOf(intentPlugin))
        }

        Intent(ACTION_DATAWEDGE).also { intent ->
            intent.putExtra(EXTRA_SET_CONFIG, profileConfig)
            context.sendBroadcast(intent)
        }
    }
}
