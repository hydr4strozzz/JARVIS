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
                withContext(Dispatchers.Main) { JarvisLogger.log("ERROR: Mic init failed! Check Permissions.") }
                isRecording = false
                return@launch
            }
            
            try {
                audioRecord.startRecording()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { JarvisLogger.log("ERROR: Mic start fail: ${e.message}") }
                isRecording = false
                return@launch
            }
            
            val buffer = ShortArray(bufferSize)
            var silenceFrames = 0
            val maxSilenceFrames = 5 
            var speechFrames = 0
            val audioBuffer = java.io.ByteArrayOutputStream()

            while (isRecording) {
                val read = audioRecord.read(buffer, 0, bufferSize)
                if (read > 0 && !isMuted) {
                    var sum = 0.0
                    for (i in 0 until read) sum += buffer[i] * buffer[i]
                    val rms = kotlin.math.sqrt(sum / read)
                    val isSpeech = rms > 150.0 

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

                    if ((silenceFrames > maxSilenceFrames || audioBuffer.size() > sampleRate * 2 * 6) && speechFrames > 0) {
                        val pcmData = audioBuffer.toByteArray()
                        audioBuffer.reset()
                        speechFrames = 0
                        silenceFrames = 0
                        
                        if (pcmData.size > 16000) {
                            val tempFile = File.createTempFile("audio", ".wav", ctx.cacheDir)
                            writeProperWavFile(tempFile, pcmData)
                            
                            withContext(Dispatchers.Main) { JarvisLogger.log("Transcribing...") }
                            val text = transcribe(tempFile)
                            tempFile.delete()
                            
                            if (!text.isNullOrBlank()) {
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

    private suspend fun transcribe(file: File): String? {
        try {
            var apiKey = Prefs.getGroqKey(ctx)
            if (apiKey.isBlank()) apiKey = BuildConfig.GROQ_API_KEY
            if (apiKey.isBlank()) {
                withContext(Dispatchers.Main) { JarvisLogger.log("ERROR: Groq Key missing!") }
                return null
            }
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", file.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", "whisper-large-v3").build()
            val request = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey").post(body).build()
            client.newCall(request).execute().use { response ->
                val resStr = response.body?.string() ?: return null
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) { JarvisLogger.log("Whisper Err ${response.code}: $resStr") }
                    return null
                }
                val json = JSONObject(resStr)
                return if (json.has("text")) json.getString("text").trim() else null
            }
        } catch (e: Exception) { 
            withContext(Dispatchers.Main) { JarvisLogger.log("Whisper Ex: ${e.message}") }
            return null 
        }
    }

    // PROPER WAV HEADER GENERATION
    private fun writeProperWavFile(file: File, pcmData: ByteArray) {
        val out = FileOutputStream(file)
        val dataLen = pcmData.size
        val totalDataLen = dataLen + 36
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(intToByteArray(totalDataLen), 0, 4)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(intToByteArray(16), 0, 4) // Subchunk1Size
        out.write(shortToByteArray(1), 0, 2) // AudioFormat (PCM)
        out.write(shortToByteArray(channels.toShort()), 0, 2)
        out.write(intToByteArray(sampleRate), 0, 4)
        out.write(intToByteArray(byteRate), 0, 4)
        out.write(shortToByteArray(blockAlign.toShort()), 0, 2)
        out.write(shortToByteArray(bitsPerSample.toShort()), 0, 2)
        
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(intToByteArray(dataLen), 0, 4)
        
        out.write(pcmData)
        out.flush()
        out.close()
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xff).toByte(),
            ((value.toInt() shr 8) and 0xff).toByte()
        )
    }

    fun stop() { isRecording = false; job?.cancel() }
}
