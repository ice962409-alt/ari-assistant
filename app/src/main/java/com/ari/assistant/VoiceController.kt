package com.ari.assistant

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ari.assistant.voz.VoiceCapture
import java.util.Locale

/**
 * Reconocimiento de voz y lectura en voz alta con los servicios de Android.
 * Todos los callbacks del [Listener] se entregan en el hilo principal.
 */
class VoiceController(
    private val activity: Activity,
    private val listener: Listener
) {

    interface Listener {
        fun onListeningStarted()
        fun onPartialSpeech(text: String)
        fun onSpeechResult(text: String)

        /** [noSpeech] indica que no se oyó nada (silencio o sin coincidencias). */
        fun onSpeechError(message: String, noSpeech: Boolean)
        fun onSpeakingFinished()

        /** Ari empezó a decir algo en voz alta (para animar su cara). */
        fun onSpeakingStarted() {}

        /** Ari va a decir una palabra de [chars] letras (para mover su boca al ritmo de la voz). */
        fun onWordSpoken(chars: Int) {}

        /** Volumen de tu voz mientras te escucha, de 0 a 1 (para animar su cara). */
        fun onVoiceLevel(level: Float) {}

        /**
         * La frase que acabas de decir, en números, para saber quién habló (solo si
         * [speakerMode] está activo). null si fue muy corta o no hablaste.
         */
        fun onSpeakerAudio(samples: FloatArray?) {}

        /** El reconocedor de este teléfono no acepta el audio que graba Ari: sin reconocer voces. */
        fun onOwnAudioUnsupported() {}
    }

    /** true para que Ari grabe ella misma la frase y así pueda saber quién habla. */
    var speakerMode = false

    /** Se pone en true si el reconocedor no aceptó el audio de Ari (Ari lo recuerda). */
    var ownAudioUnsupported = false

    private var capture: VoiceCapture? = null
    private var captureReadSide: ParcelFileDescriptor? = null

    private val locale: Locale = Locale.getDefault()
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    // El motor de voz del teléfono no arrancó: Ari no habla, pero la plática sigue (te escucha y escribe).
    private var ttsFailed = false
    private var pendingSpeech: String? = null
    private var utteranceCounter = 0
    private var lastUtteranceId: String? = null

    // Respuesta que se dice frase por frase mientras Gemma la escribe.
    private val streamIds = mutableSetOf<String>()
    private val streamWaiting = mutableListOf<String>()
    private var streamOpen = false
    private var streamActive = false

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(activity)

    init {
        tts = TextToSpeech(activity) { status ->
            activity.runOnUiThread {
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    configureTts()
                    pendingSpeech?.let { speak(it) }
                    streamWaiting.toList().also { streamWaiting.clear() }.forEach { enqueue(it) }
                } else {
                    ttsFailed = true
                    val waiting = pendingSpeech != null
                    pendingSpeech = null
                    streamWaiting.clear()
                    if (waiting) listener.onSpeakingFinished()
                    finishStreamIfDone()
                }
                pendingSpeech = null
            }
        }
    }

    private fun configureTts() {
        val engine = tts ?: return
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.setLanguage(Locale.forLanguageTag("es"))
        }
        applyStyle()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                activity.runOnUiThread { listener.onSpeakingStarted() }
            }

            override fun onDone(utteranceId: String?) {
                activity.runOnUiThread {
                    if (utteranceId != null && utteranceId == lastUtteranceId) {
                        lastUtteranceId = null
                        listener.onSpeakingFinished()
                    }
                    if (utteranceId != null && streamIds.remove(utteranceId)) finishStreamIfDone()
                }
            }

            // La voz del teléfono avisa cada palabra que va a decir (si sabe hacerlo).
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val chars = (end - start).coerceIn(1, 20)
                activity.runOnUiThread { listener.onWordSpoken(chars) }
            }

            @Deprecated("Requerido por versiones anteriores de la API")
            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onDone(utteranceId)
            }
        })
    }

    fun startListening() {
        stopSpeaking()

        val activeRecognizer = recognizer
            ?: SpeechRecognizer.createSpeechRecognizer(activity).also {
                it.setRecognitionListener(recognitionListener)
                recognizer = it
            }

        stopCapture()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Al terminar de hablar, Ari contesta enseguida (si el reconocedor lo permite).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        }

        if (speakerMode && !ownAudioUnsupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Ari graba el micrófono y le pasa el audio al reconocedor por una "tubería": así la
            // misma frase sirve para entender qué dijiste y para saber quién lo dijo.
            val (readSide, writeSide) = ParcelFileDescriptor.createPipe()
            captureReadSide = readSide
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, VoiceCapture.RATE)
            val started = VoiceCapture(
                pipe = writeSide,
                onLevel = { level -> activity.runOnUiThread { listener.onVoiceLevel(level) } },
                onFinished = { result -> activity.runOnUiThread { onCaptureFinished(result) } }
            )
            capture = started
            activeRecognizer.startListening(intent)
            started.start()
            return
        }

        activeRecognizer.startListening(intent)
    }

    private fun onCaptureFinished(result: VoiceCapture.Result) {
        if (result.stalled) {
            // El reconocedor nunca leyó el audio: en este teléfono se escucha como antes.
            ownAudioUnsupported = true
            stopCapture()
            recognizer?.cancel()
            listener.onOwnAudioUnsupported()
            startListening()
            return
        }
        listener.onSpeakerAudio(result.samples)
    }

    private fun stopCapture() {
        capture?.stop()
        capture = null
    }

    private fun closeReadSide() {
        captureReadSide?.let { runCatching { it.close() } }
        captureReadSide = null
    }

    fun stopListening() {
        stopCapture()
        recognizer?.cancel()
        closeReadSide()
    }

    /** Lee [text] en voz alta; al terminar llama a [Listener.onSpeakingFinished]. */
    /** Aplica la velocidad y el tono que eligió el usuario (Ajustes → Voz de Ari). */
    fun applyStyle() {
        val engine = tts ?: return
        val prefs = activity.getSharedPreferences("ari_voces", android.content.Context.MODE_PRIVATE)
        engine.setSpeechRate(VoiceStyle.rate(prefs))
        engine.setPitch(VoiceStyle.pitch(prefs))
    }

    fun speak(text: String) {
        val clean = cleanForSpeech(text)
        if (clean.isEmpty()) {
            listener.onSpeakingFinished()
            return
        }

        val engine = tts
        if (ttsFailed) {
            listener.onSpeakingFinished()
            return
        }
        if (engine == null || !ttsReady) {
            // El motor aún se está iniciando; se leerá cuando esté listo.
            pendingSpeech = clean
            return
        }

        val chunks = splitForSpeech(clean, TextToSpeech.getMaxSpeechInputLength() - 100)
        chunks.forEachIndexed { index, chunk ->
            val id = "ari_${utteranceCounter++}"
            if (index == chunks.lastIndex) lastUtteranceId = id
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, mode, null, id)
        }
    }

    /** Empieza una respuesta que llegará por frases con [streamSentence]. */
    fun beginStream() {
        stopSpeaking()
        streamOpen = true
        streamActive = true
    }

    /** Dice una frase de la respuesta en curso, en cuanto el motor de voz pueda. */
    fun streamSentence(text: String) {
        if (!streamActive) return
        val clean = cleanForSpeech(text)
        if (clean.isEmpty() || ttsFailed) return
        if (tts == null || !ttsReady) {
            streamWaiting.add(clean)
        } else {
            enqueue(clean)
        }
    }

    /**
     * No llegarán más frases: al terminar la última se avisa [Listener.onSpeakingFinished].
     * Devuelve false si la respuesta ya se había interrumpido (nadie va a avisar).
     */
    fun endStream(): Boolean {
        if (!streamActive) return false
        streamOpen = false
        finishStreamIfDone()
        return true
    }

    private fun enqueue(text: String) {
        val engine = tts ?: return
        splitForSpeech(text, TextToSpeech.getMaxSpeechInputLength() - 100).forEach { chunk ->
            val id = "ari_stream_${utteranceCounter++}"
            streamIds.add(id)
            engine.speak(chunk, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    private fun finishStreamIfDone() {
        if (streamActive && !streamOpen && streamIds.isEmpty() && streamWaiting.isEmpty()) {
            streamActive = false
            listener.onSpeakingFinished()
        }
    }

    val isSpeaking: Boolean
        get() = streamActive || lastUtteranceId != null || pendingSpeech != null

    fun stopSpeaking() {
        pendingSpeech = null
        lastUtteranceId = null
        streamIds.clear()
        streamWaiting.clear()
        streamOpen = false
        streamActive = false
        tts?.stop()
    }

    fun stopAll() {
        stopListening()
        stopSpeaking()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener.onListeningStarted()
        }

        override fun onBeginningOfSpeech() {}
        // El volumen llega más o menos entre -2 (silencio) y 10 (voz fuerte).
        override fun onRmsChanged(rmsdB: Float) {
            listener.onVoiceLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (!text.isNullOrBlank()) listener.onPartialSpeech(text)
        }

        override fun onResults(results: Bundle?) {
            closeReadSide()
            val text = firstResult(results)
            if (text.isNullOrBlank()) {
                listener.onSpeechError("No te escuché.", noSpeech = true)
            } else {
                listener.onSpeechResult(text)
            }
        }

        override fun onError(error: Int) {
            stopCapture()
            closeReadSide()
            val noSpeech = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            listener.onSpeechError(errorMessage(error), noSpeech)
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Ari no tiene permiso para usar el micrófono."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "El reconocimiento de voz necesita internet o el paquete de español sin conexión."
        SpeechRecognizer.ERROR_AUDIO -> "No se pudo usar el micrófono."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento de voz está ocupado."
        else -> "Error de reconocimiento de voz ($error)."
    }

    companion object {
        /** Quita el formato Markdown que no tiene sentido leer en voz alta. */
        fun cleanForSpeech(text: String): String =
            text.replace(Regex("```[\\s\\S]*?```"), " ")
                .replace(Regex("[*_#`>|~]"), "")
                .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("\\s+"), " ")
                .trim()

        /** Divide el texto en trozos que el motor de voz acepte, cortando en frases. */
        fun splitForSpeech(text: String, maxLength: Int): List<String> {
            if (text.length <= maxLength) return listOf(text)

            val chunks = mutableListOf<String>()
            var current = StringBuilder()
            text.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
                if (current.length + sentence.length + 1 > maxLength && current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current = StringBuilder()
                }
                if (sentence.length > maxLength) {
                    sentence.chunked(maxLength).forEach { chunks.add(it) }
                } else {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(sentence)
                }
            }
            if (current.isNotEmpty()) chunks.add(current.toString())
            return chunks
        }
    }
}
