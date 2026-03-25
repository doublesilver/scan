package com.scan.warehouse.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

object ServerDiscovery {

    private const val PORT = 8000
    private const val TIMEOUT_MS = 500

    suspend fun findServer(): String? = withContext(Dispatchers.IO) {
        val subnet = getSubnet() ?: return@withContext null

        (1..254).map { i ->
            async {
                val ip = "$subnet.$i"
                try {
                    val url = URL("http://$ip:$PORT/health")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = TIMEOUT_MS
                    conn.readTimeout = TIMEOUT_MS
                    conn.requestMethod = "GET"
                    if (conn.responseCode == 200) "http://$ip:$PORT" else null
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().firstOrNull { it != null }
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
