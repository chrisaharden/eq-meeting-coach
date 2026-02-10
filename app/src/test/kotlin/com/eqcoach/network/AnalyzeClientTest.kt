package com.eqcoach.network

import com.eqcoach.model.Verdict
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AnalyzeClientTest {

    private val server = MockWebServer()
    private lateinit var client: AnalyzeClient

    private val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)
    private val fakeWav = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"

    @Before
    fun setUp() {
        server.start()
        client = AnalyzeClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 2,
        )
    }

    @After
    fun tearDown() {
        client.shutdown()
        server.shutdown()
    }

    @Test
    fun `successful GREEN verdict`() = runTest {
        server.enqueue(MockResponse().setBody("""{"verdict":"GREEN"}"""))
        val result = client.analyze(fakeJpeg, fakeWav)
        assertEquals(Verdict.GREEN, result)
    }

    @Test
    fun `successful YELLOW verdict`() = runTest {
        server.enqueue(MockResponse().setBody("""{"verdict":"YELLOW"}"""))
        val result = client.analyze(fakeJpeg, fakeWav)
        assertEquals(Verdict.YELLOW, result)
    }

    @Test
    fun `successful RED verdict`() = runTest {
        server.enqueue(MockResponse().setBody("""{"verdict":"RED"}"""))
        val result = client.analyze(fakeJpeg, fakeWav)
        assertEquals(Verdict.RED, result)
    }

    @Test
    fun `request sends correct multipart parts`() = runTest {
        server.enqueue(MockResponse().setBody("""{"verdict":"GREEN"}"""))
        client.analyze(fakeJpeg, fakeWav)

        val request = server.takeRequest()
        assertEquals("POST", request.method)

        val contentType = request.getHeader("Content-Type") ?: ""
        assertTrue("Expected multipart/form-data", contentType.startsWith("multipart/form-data"))

        val body = request.body.readUtf8()
        assertTrue("Expected frame part", body.contains("name=\"frame\""))
        assertTrue("Expected audio part", body.contains("name=\"audio\""))
        assertTrue("Expected frame filename", body.contains("filename=\"frame.jpg\""))
        assertTrue("Expected audio filename", body.contains("filename=\"audio.wav\""))
        assertTrue("Expected image/jpeg content type", body.contains("image/jpeg"))
        assertTrue("Expected audio/wav content type", body.contains("audio/wav"))
    }

    @Test
    fun `request hits analyze endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"verdict":"GREEN"}"""))
        client.analyze(fakeJpeg, fakeWav)

        val request = server.takeRequest()
        assertEquals("/analyze", request.path)
    }

    @Test
    fun `server 500 throws ServerException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            client.analyze(fakeJpeg, fakeWav)
            fail("Expected ServerException")
        } catch (e: ServerException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `server 422 throws ServerException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(422))
        try {
            client.analyze(fakeJpeg, fakeWav)
            fail("Expected ServerException")
        } catch (e: ServerException) {
            assertTrue(e.message!!.contains("422"))
        }
    }

    @Test
    fun `malformed JSON throws ServerException`() = runTest {
        server.enqueue(MockResponse().setBody("""{"bad":"data"}"""))
        try {
            client.analyze(fakeJpeg, fakeWav)
            fail("Expected ServerException")
        } catch (e: ServerException) {
            assertTrue(e.message!!.contains("Invalid response"))
        }
    }

    @Test
    fun `empty body throws ServerException`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        try {
            client.analyze(fakeJpeg, fakeWav)
            fail("Expected ServerException")
        } catch (e: ServerException) {
            // Empty body or invalid JSON — both acceptable error paths
        }
    }

    @Test
    fun `connection timeout throws ServerException`() = runTest {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        try {
            client.analyze(fakeJpeg, fakeWav)
            fail("Expected ServerException")
        } catch (e: ServerException) {
            assertTrue(e.message!!.contains("Connection failed"))
        }
    }

    @Test
    fun `cancelInflight cancels in-flight request`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"verdict":"GREEN"}""")
                .setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS),
        )

        val job = launch {
            try {
                client.analyze(fakeJpeg, fakeWav)
                fail("Expected ServerException from cancellation")
            } catch (_: ServerException) {
                // Expected — call was cancelled
            }
        }

        // Give the request time to start, then cancel
        kotlinx.coroutines.delay(200)
        client.cancelInflight()
        job.join()
    }

    @Test
    fun `unknown keys in response are ignored`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"verdict":"GREEN","confidence":0.95,"extra":"ignored"}"""),
        )
        val result = client.analyze(fakeJpeg, fakeWav)
        assertEquals(Verdict.GREEN, result)
    }
}
