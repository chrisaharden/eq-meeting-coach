package com.eqcoach.viewmodel

import com.eqcoach.model.SessionState
import com.eqcoach.model.Verdict
import com.eqcoach.service.CaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SessionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SessionViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is IDLE with GRAY verdict`() {
        assertEquals(SessionState.IDLE, viewModel.sessionState.value)
        assertEquals(Verdict.GRAY, viewModel.currentVerdict.value)
    }

    @Test
    fun `startSession transitions to ACTIVE with GRAY verdict`() {
        viewModel.startSession()

        assertEquals(SessionState.ACTIVE, viewModel.sessionState.value)
        assertEquals(Verdict.GRAY, viewModel.currentVerdict.value)
    }

    @Test
    fun `stopSession transitions back to IDLE`() {
        viewModel.startSession()
        viewModel.stopSession()

        assertEquals(SessionState.IDLE, viewModel.sessionState.value)
        assertEquals(Verdict.GRAY, viewModel.currentVerdict.value)
    }

    @Test
    fun `double startSession is idempotent`() {
        viewModel.startSession()
        viewModel.startSession()

        assertEquals(SessionState.ACTIVE, viewModel.sessionState.value)
    }

    @Test
    fun `stopSession while IDLE is safe`() {
        viewModel.stopSession()

        assertEquals(SessionState.IDLE, viewModel.sessionState.value)
        assertEquals(Verdict.GRAY, viewModel.currentVerdict.value)
    }

    @Test
    fun `startSession calls captureService startCapture`() {
        val recorder = RecordingCaptureService()
        viewModel.setCaptureService(recorder)

        viewModel.startSession()

        assertEquals(listOf("startCapture"), recorder.calls)
    }

    @Test
    fun `stopSession calls captureService stopCapture`() {
        val recorder = RecordingCaptureService()
        viewModel.setCaptureService(recorder)

        viewModel.startSession()
        recorder.calls.clear()
        viewModel.stopSession()

        assertEquals(listOf("stopCapture"), recorder.calls)
    }

    @Test
    fun `full lifecycle start then stop`() {
        val recorder = RecordingCaptureService()
        viewModel.setCaptureService(recorder)

        assertEquals(SessionState.IDLE, viewModel.sessionState.value)

        viewModel.startSession()
        assertEquals(SessionState.ACTIVE, viewModel.sessionState.value)

        viewModel.stopSession()
        assertEquals(SessionState.IDLE, viewModel.sessionState.value)
        assertEquals(listOf("startCapture", "stopCapture"), recorder.calls)
    }

    /** Simple test double that records method calls. */
    private class RecordingCaptureService : CaptureService {
        val calls = mutableListOf<String>()

        override fun startCapture() { calls.add("startCapture") }
        override fun stopCapture() { calls.add("stopCapture") }
        override suspend fun getCurrentVerdict(): Verdict = Verdict.GRAY
    }
}
