package com.scan.warehouse.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = 2
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null

        for (attempt in 0..maxRetries) {
            try {
                val response = chain.proceed(request)

                if (response.code in 500..599 && attempt < maxRetries) {
                    response.close()
                    continue
                }

                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxRetries) throw e
            }
        }

        throw lastException ?: IOException("Retry failed")
    }
}
