package com.jarvis.assistant.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.util.JarvisLogger
import com.jarvis.assistant.util.Prefs
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AudioRecorderManager(private val ctx: Context, private val onTextResult: (String) -> Unit) {
    private var isRecording = false
    private var isMuted = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return
        isRecording = true
        job = scope.launch {
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                JarvisLogger.log("Mic init failed")
                isRecording = false
                return@launch
            }
            audioRecord.startRecording()
            val buffer = ShortArray(bufferSize)
            
            var silenceFrames = 0
            val maxSilenceFrames = 10 // ~1.6 seconds of silence ends the recording
            val minSpeechFrames = 2
            var speechFrames = 0
            val audioBuffer = java.io.ByteArrayOutputStream()

            while (isRecording) {
                val read = audioRecord.read(buffer, 0, bufferSize)
                if (read > 0 && !isMuted) {
                    var sum = 0.0
                    for (i in 0 until read) sum += buffer[i] * buffer[i]
                    val rms = kotlin.math.sqrt(sum / read)
                    
                    val isSpeech = rms > 800.0 // Voice threshold

                    if (isSpeech) {
                        speechFrames++
                        silenceFrames = 0
                    } else {
                        if (speechFrames > 0) silenceFrames++
                    }

                    if (speechFrames > 0) {
                        val byteBuffer = ByteArray(read * 2)
                        for (i in 0 until read) {
                            byteBuffer[i * 2] = (buffer[i].toInt() and 0xff).toByte()
                            byteBuffer[i * 2 + 1] = ((buffer[i].toInt() shr 8) and 0xff).toByte()
                        }
                        audioBuffer.write(byteBuffer)
                    }

                    // Trigger transcription when silence is detected or max length reached
                    if ((silenceFrames > maxSilenceFrames || audioBuffer.size() > sampleRate * 2 * 8) && speechFrames > minSpeechFrames) {
                        val pcmData = audioBuffer.toByteArray()
                        audioBuffer.reset()
                        speechFrames = 0
                        silenceFrames = 0
                        
                        val tempFile = File.createTempFile("audio", ".wav", ctx.cacheDir)
                        writeWavFileWithData(tempFile, pcmData)
                        
                        withContext(Dispatchers.IO) {
                            val text = transcribe(tempFile)
                            tempFile.delete()
                            if (!text.isNullOrBlank() && text.length > 2) {
                                JarvisLogger.log("Whisper: $text")
                                withContext(Dispatchers.Main) { onTextResult(text) }
                            }
                        }
                    } else if (silenceFrames > maxSilenceFrames) {
                        audioBuffer.reset()
                        speechFrames = 0
                        silenceFrames = 0
                    }
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }
    }

    fun muteMic() { isMuted = true }
    fun unmuteMic() { isMuted = false }

    private fun transcribe(file: File): String? {
        try {
            var apiKey = Prefs.getGroqKey(ctx)
            if (apiKey.isBlank()) apiKey = BuildConfig.GROQ_API_KEY
            if (apiKey.isBlank()) return null
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", file.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", "whisper-large-v3").build()
            val request = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey").post(body).build()
            client.newCall(request).execute().use { response ->
                val resStr = response.body?.string() ?: return null
                val json = JSONObject(resStr)
                return if (json.has("text")) json.getString("text").trim() else null
            }
        } catch (e: Exception) { return null }
    }

    private fun writeWavFileWithData(file: File, pcmData: ByteArray) {
        val out = FileOutputStream(file)
        val dataLen = pcmData.size
        val header = ByteArray(44)
        val totalLen = dataLen + 36

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalLen and 0xff).toByte(); header[5] = ((totalLen shr 8) and 0xff).toByte(); header[6] = ((totalLen shr 16) and 0xff).toByte(); header[7] = ((totalLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte(); header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (sampleRate * 2 and 0xff).toByte(); header[29] = ((sampleRate * 2 shr 8) and 0xff).toByte(); header[30] = 2; header[31] = 0; header[32] = 16; header[33] = 0
        header[34] = 'd'.code.toByte(); header[35] = 'a'.code.toByte(); header[36] = 't'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = (dataLen and 0xff).toByte(); header[39] = ((dataLen shr 8) and 0xff).toByte(); header[40] = ((dataLen shr 16) and 0xff).toByte(); header[41] = ((dataLen shr 24) and 0xff).toByte()

        out.write(header)
        out.write(pcmData)
        out.close()
    }
    fun stop() { isRecording = false; job?.cancel() }
}
