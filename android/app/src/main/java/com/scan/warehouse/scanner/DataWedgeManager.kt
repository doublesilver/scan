package com.scan.warehouse.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.channels.BufferOverflow
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

    private val _scanFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scanFlow: SharedFlow<String> = _scanFlow.asSharedFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun resetBuffer() {
        _scanFlow.resetReplayCache()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SCAN) {
                val raw = intent.getStringExtra(EXTRA_DATA)?.trim() ?: return
                if (!raw.matches(barcodeRegex) && !raw.startsWith("BOX-")) return
                _scanFlow.tryEmit(raw)
            }
        }
    }

    fun register(context: Context) {
        val appContext = context.applicationContext
        if (isRegistered) unregister(appContext)
        val filter = IntentFilter(ACTION_SCAN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                receiver, filter,
                "com.symbol.datawedge.permission.DATAWEDGE",
                null,
                Context.RECEIVER_EXPORTED
            )
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        isRegistered = true
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        try { context.applicationContext.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
        isRegistered = false
    }

    fun setupProfile(context: Context) {
        val appConfig = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val config = Bundle().apply {
            putString("PROFILE_NAME", "WarehouseScanner")
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
            putParcelableArray("APP_LIST", arrayOf(appConfig))
            putParcelableArray("PLUGIN_CONFIG", arrayOf(
                Bundle().apply {
                    putString("PLUGIN_NAME", "KEYSTROKE")
                    putString("RESET_CONFIG", "true")
                    putBundle("PARAM_LIST", Bundle().apply {
                        putString("keystroke_output_enabled", "false")
                    })
                },
                Bundle().apply {
                    putString("PLUGIN_NAME", "INTENT")
                    putString("RESET_CONFIG", "true")
                    putBundle("PARAM_LIST", Bundle().apply {
                        putString("intent_output_enabled", "true")
                        putString("intent_action", ACTION_SCAN)
                        putString("intent_delivery", "2")
                    })
                }
            ))
        }
        Intent(ACTION_DATAWEDGE).also {
            it.putExtra(EXTRA_SET_CONFIG, config)
            context.sendBroadcast(it)
        }
    }
}
