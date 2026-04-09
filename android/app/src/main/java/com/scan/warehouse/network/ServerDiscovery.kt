package com.scan.warehouse.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

object ServerDiscovery {

    private const val PORT = 8000
    private const val TIMEOUT_MS = 500
    private const val PREF_NAME = "warehouse_settings"
    private const val KEY_LAST_IP = "last_known_server_ip"

    suspend fun findServer(context: Context? = null): String? = withContext(Dispatchers.IO) {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastIp = prefs.getString(KEY_LAST_IP, null)
            if (!lastIp.isNullOrBlank()) {
                val candidate = "http://$lastIp:$PORT"
                if (healthCheck(candidate)) {
                    return@withContext candidate
                }
            }
        }

        val subnet = getSubnet() ?: return@withContext null

        for (chunk in (1..254).chunked(4)) {
            val result = coroutineScope {
                chunk.map { i ->
                    async {
                        val ip = "$subnet.$i"
                        var conn: HttpURLConnection? = null
                        try {
                            val url = URL("http://$ip:$PORT/health")
                            conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = TIMEOUT_MS
                            conn.readTimeout = TIMEOUT_MS
                            conn.requestMethod = "GET"
                            if (conn.responseCode == 200) "http://$ip:$PORT" else null
                        } catch (_: Exception) {
                            null
                        } finally {
                            conn?.disconnect()
                        }
                    }
                }.awaitAll().firstOrNull { it != null }
            }
            if (result != null) {
                if (context != null) {
                    val ip = result.removePrefix("http://").substringBefore(":")
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_LAST_IP, ip).apply()
                }
                return@withContext result
            }
        }
        null
    }

    private fun healthCheck(baseUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$baseUrl/health")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun getSubnet(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is InetAddress && !addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split(".")
                        if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
