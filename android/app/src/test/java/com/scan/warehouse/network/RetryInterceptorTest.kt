package com.scan.warehouse.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RetryInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 3, initialDelayMs = 10))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `성공 응답은 재시도 없이 반환`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(
            Request.Builder().url(server.url("/")).build()
        ).execute()

        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `5xx 에러 시 최대 3회 재시도 후 마지막 응답 반환`() {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(500))
        }

        val response = client.newCall(
            Request.Builder().url(server.url("/")).build()
        ).execute()

        assertEquals(500, response.code)
        assertEquals(4, server.requestCount) // 1 original + 3 retries
    }

    @Test
    fun `5xx 후 성공하면 성공 응답 반환`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("recovered"))

        val response = client.newCall(
            Request.Builder().url(server.url("/")).build()
        ).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `4xx 에러는 재시도하지 않음`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val response = client.newCall(
            Request.Builder().url(server.url("/")).build()
        ).execute()

        assertEquals(404, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `400 에러는 재시도하지 않음`() {
        server.enqueue(MockResponse().setResponseCode(400))

        val response = client.newCall(
            Request.Builder().url(server.url("/")).build()
        ).execute()

        assertEquals(400, response.code)
        assertEquals(1, server.requestCount)
    }
}
