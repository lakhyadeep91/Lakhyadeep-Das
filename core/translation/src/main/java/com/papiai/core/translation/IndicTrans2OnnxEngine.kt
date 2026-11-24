package com.papiai.core.translation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.papiai.core.ai.AiModelLifecycleManager
import com.papiai.domain.model.SupportedLanguage
import com.papiai.domain.model.TranslationResult
import java.io.File
import java.nio.LongBuffer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class IndicTrans2Vocabulary(
  val tokenToId: Map<String, Int>,
  val idToToken: Map<Int, String>,
  val eosId: Int,
  val padId: Int,
  val unkId: Int,
) {
  fun encode(text: String, srcCode: String, tgtCode: String): LongArray {
    val ids = mutableListOf<Long>()
    // IndicTrans2 prefixes sequences with source and target FLORES language tags
    tokenToId[srcCode]?.let { ids.add(it.toLong()) }
    tokenToId[tgtCode]?.let { ids.add(it.toLong()) }

    val normalized = text.trim().replace(Regex("\\s+"), " ")
    if (normalized.isNotEmpty()) {
      for (word in normalized.split(" ")) {
        val prefixed = "\u2581$word"
        val direct = tokenToId[prefixed] ?: tokenToId[word]
        if (direct != null) {
          ids.add(direct.toLong())
        } else {
          greedySubwordTokenize(prefixed, ids)
        }
      }
    }
    ids.add(eosId.toLong())
    return ids.toLongArray()
  }

  private fun greedySubwordTokenize(piece: String, out: MutableList<Long>) {
    var start = 0
    while (start < piece.length) {
      var end = piece.length
      var matchedId: Int? = null
      while (end > start) {
        val sub = piece.substring(start, end)
        val id = tokenToId[sub]
        if (id != null) {
          matchedId = id
          break
        }
        end--
      }
      if (matchedId != null) {
        out.add(matchedId.toLong())
        start = end
      } else {
        out.add(unkId.toLong())
        start++
      }
    }
  }

  fun decode(ids: List<Int>): String {
    val sb = StringBuilder()
    for (id in ids) {
      if (id == eosId || id == padId) continue
      val tok = idToToken[id] ?: continue
      if (tok.startsWith("<") && tok.endsWith(">")) continue
      if (tok.endsWith("_Latn") || tok.endsWith("_Deva") || tok.endsWith("_Orya")) continue
      sb.append(tok.replace('\u2581', ' '))
    }
    return sb.toString().replace(Regex("\\s+"), " ").trim()
  }

  companion object {
    fun loadFromDir(dir: File): IndicTrans2Vocabulary? {
      val vocabJson = File(dir, "vocab.json")
      if (vocabJson.exists()) {
        val obj = JSONObject(vocabJson.readText())
        val map = HashMap<String, Int>(obj.length())
        val rev = HashMap<Int, String>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          val v = obj.optInt(k, -1)
          if (v >= 0) {
            map[k] = v
            rev[v] = k
          }
        }
        return IndicTrans2Vocabulary(
          tokenToId = map,
          idToToken = rev,
          eosId = map["</s>"] ?: 2,
          padId = map["<pad>"] ?: 1,
          unkId = map["<unk>"] ?: 3,
        )
      }

      val tgtVocab = File(dir, "tgt_vocab.txt")
      val srcVocab = File(dir, "src_vocab.txt")
      if (srcVocab.exists() && tgtVocab.exists()) {
        val map = HashMap<String, Int>()
        srcVocab.readLines().forEachIndexed { idx, line ->
          val token = line.substringBefore(' ').trim()
          if (token.isNotEmpty()) map[token] = idx
        }
        val rev = HashMap<Int, String>()
        tgtVocab.readLines().forEachIndexed { idx, line ->
          val token = line.substringBefore(' ').trim()
          if (token.isNotEmpty()) rev[idx] = token
        }
        return IndicTrans2Vocabulary(
          tokenToId = map,
          idToToken = rev,
          eosId = map["</s>"] ?: 2,
          padId = map["<pad>"] ?: 1,
          unkId = map["<unk>"] ?: 3,
        )
      }
      return null
    }
  }
}

class IndicTrans2OnnxEngine(
  private val lifecycleManager: AiModelLifecycleManager,
) {
  private val mutex = Mutex()
  private var ortEnv: OrtEnvironment? = null
  private var encoderSession: OrtSession? = null
  private var decoderSession: OrtSession? = null
  private var loadedModelId: String? = null
  private var loadedVocab: IndicTrans2Vocabulary? = null

  suspend fun translate(
    text: String,
    source: SupportedLanguage,
    target: SupportedLanguage,
    enToIndicDir: File?,
    indicToEnDir: File?,
  ): Result<TranslationResult> = withContext(Dispatchers.IO) {
    mutex.withLock {
      val start = System.currentTimeMillis()
      if (source == target) {
        return@withLock Result.success(
          TranslationResult(
            translatedText = text,
            sourceLanguage = source,
            targetLanguage = target,
            engineName = "Identity (Same Language)",
            isOffline = true,
            durationMs = 0L,
          )
        )
      }

      val paragraphs = text.split("\n\n")
      return@withLock when {
        source == SupportedLanguage.ENGLISH -> {
          if (enToIndicDir == null) {
            Result.failure(
              IllegalStateException("IndicTrans2 (En -> Indic) ONNX model is not installed. Download it in Model Manager.")
            )
          } else {
            runTranslationPass("indictrans2-en-indic", enToIndicDir, paragraphs, source, target, start)
          }
        }
        target == SupportedLanguage.ENGLISH -> {
          if (indicToEnDir == null) {
            Result.failure(
              IllegalStateException("IndicTrans2 (Indic -> En) ONNX model is not installed. Download it in Model Manager.")
            )
          } else {
            runTranslationPass("indictrans2-indic-en", indicToEnDir, paragraphs, source, target, start)
          }
        }
        else -> {
          // Hindi <-> Odia via English pivot using the two verified distilled 200M ONNX models
          if (indicToEnDir == null || enToIndicDir == null) {
            Result.failure(
              IllegalStateException(
                "Translating ${source.englishName} -> ${target.englishName} offline pivots via English and requires both IndicTrans2 ONNX models installed."
              )
            )
          } else {
            val step1 = runTranslationPass("indictrans2-indic-en", indicToEnDir, paragraphs, source, SupportedLanguage.ENGLISH, start)
            step1.fold(
              onSuccess = { intermediate ->
                val enParagraphs = intermediate.translatedText.split("\n\n")
                runTranslationPass(
                  "indictrans2-en-indic",
                  enToIndicDir,
                  enParagraphs,
                  SupportedLanguage.ENGLISH,
                  target,
                  start,
                  noteOverride = "Translated offline via IndicTrans2 200M INT8 (English pivot)",
                )
              },
              onFailure = { Result.failure(it) },
            )
          }
        }
      }
    }
  }

  private suspend fun runTranslationPass(
    modelId: String,
    modelDir: File,
    paragraphs: List<String>,
    source: SupportedLanguage,
    target: SupportedLanguage,
    startTimeMs: Long,
    noteOverride: String? = null,
  ): Result<TranslationResult> {
    val acquired = lifecycleManager.acquireModelSlot(modelId, minRequiredRamMb = 900) {
      releaseInternal()
    }
    if (!acquired) {
      return Result.failure(
        IllegalStateException("Insufficient free RAM (need >= 900 MB) to load IndicTrans2 ONNX on this device.")
      )
    }

    return try {
      ensureLoaded(modelId, modelDir)
      val env = ortEnv!!
      val enc = encoderSession!!
      val dec = decoderSession!!
      val vocab = loadedVocab!!

      val translatedParagraphs = paragraphs.map { para ->
        if (para.isBlank()) {
          para
        } else {
          val lines = para.split("\n")
          lines.joinToString("\n") { line ->
            if (line.isBlank()) line else decodeGreedy(env, enc, dec, vocab, line, source, target)
          }
        }
      }

      Result.success(
        TranslationResult(
          translatedText = translatedParagraphs.joinToString("\n\n"),
          sourceLanguage = source,
          targetLanguage = target,
          engineName = "AI4Bharat IndicTrans2 200M INT8 (ONNX)",
          isOffline = true,
          durationMs = System.currentTimeMillis() - startTimeMs,
          statusNote = noteOverride,
        )
      )
    } catch (t: Throwable) {
      Result.failure(
        IllegalStateException("IndicTrans2 ONNX inference error: ${t.message}", t)
      )
    }
  }

  private fun ensureLoaded(modelId: String, modelDir: File) {
    if (loadedModelId == modelId && encoderSession != null && decoderSession != null && loadedVocab != null) {
      return
    }
    releaseInternal()

    val encoderFile = listOf("encoder.onnx", "encoder_model.onnx")
      .map { File(modelDir, it) }
      .firstOrNull { it.exists() }
      ?: throw IllegalStateException("Missing encoder ONNX file in ${modelDir.name}")

    val decoderFile = listOf("decoder.onnx", "decoder_model.onnx")
      .map { File(modelDir, it) }
      .firstOrNull { it.exists() }
      ?: throw IllegalStateException("Missing decoder ONNX file in ${modelDir.name}")

    val vocab = IndicTrans2Vocabulary.loadFromDir(modelDir)
      ?: throw IllegalStateException("Missing IndicTrans2 vocabulary files in ${modelDir.name}")

    val env = OrtEnvironment.getEnvironment()
    val opts = OrtSession.SessionOptions().apply {
      setIntraOpNumThreads(4)
      setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
    }
    ortEnv = env
    encoderSession = env.createSession(encoderFile.absolutePath, opts)
    decoderSession = env.createSession(decoderFile.absolutePath, opts)
    loadedVocab = vocab
    loadedModelId = modelId
  }

  private fun decodeGreedy(
    env: OrtEnvironment,
    encoder: OrtSession,
    decoder: OrtSession,
    vocab: IndicTrans2Vocabulary,
    text: String,
    source: SupportedLanguage,
    target: SupportedLanguage,
  ): String {
    val inputIds = vocab.encode(text, source.indicTransCode, target.indicTransCode)
    val attentionMask = LongArray(inputIds.size) { 1L }
    val seqShape = longArrayOf(1L, inputIds.size.toLong())

    OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), seqShape).use { inputTensor ->
      OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), seqShape).use { maskTensor ->
        val encInputs = buildMap<String, OnnxTensor> {
          if ("input_ids" in encoder.inputNames) put("input_ids", inputTensor)
          if ("attention_mask" in encoder.inputNames) put("attention_mask", maskTensor)
        }
        encoder.run(encInputs).use { encOutputs ->
          val encoderHidden = encOutputs[0] as OnnxTensor
          val generated = mutableListOf(vocab.eosId)
          val maxSteps = (inputIds.size * 2 + 16).coerceIn(32, 128)

          for (step in 0 until maxSteps) {
            val decIds = generated.map { it.toLong() }.toLongArray()
            val decShape = longArrayOf(1L, decIds.size.toLong())
            OnnxTensor.createTensor(env, LongBuffer.wrap(decIds), decShape).use { decInputTensor ->
              val decInputs = buildMap<String, OnnxTensor> {
                if ("input_ids" in decoder.inputNames) put("input_ids", decInputTensor)
                if ("decoder_input_ids" in decoder.inputNames) put("decoder_input_ids", decInputTensor)
                if ("encoder_hidden_states" in decoder.inputNames) put("encoder_hidden_states", encoderHidden)
                if ("encoder_attention_mask" in decoder.inputNames) put("encoder_attention_mask", maskTensor)
              }
              decoder.run(decInputs).use { decOut ->
                val logitsTensor = decOut[0] as OnnxTensor
                val logits = logitsTensor.floatBuffer
                val vocabSize = logitsTensor.info.shape.last().toInt()
                val offset = (decIds.size - 1) * vocabSize
                var bestId = vocab.eosId
                var bestScore = Float.NEGATIVE_INFINITY
                for (i in 0 until vocabSize) {
                  val score = logits.get(offset + i)
                  if (score > bestScore) {
                    bestScore = score
                    bestId = i
                  }
                }
                if (bestId == vocab.eosId) {
                  return vocab.decode(generated.drop(1))
                }
                generated.add(bestId)
              }
            }
          }
          return vocab.decode(generated.drop(1))
        }
      }
    }
  }

  fun releaseInternal() {
    runCatching { encoderSession?.close() }
    runCatching { decoderSession?.close() }
    encoderSession = null
    decoderSession = null
    loadedVocab = null
    loadedModelId = null
  }
}

class OptionalLibreTranslateClient {
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  suspend fun translate(
    baseUrl: String,
    text: String,
    source: SupportedLanguage,
    target: SupportedLanguage,
  ): Result<TranslationResult> = withContext(Dispatchers.IO) {
    val cleanUrl = baseUrl.trim().trimEnd('/')
    if (cleanUrl.isBlank() || (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://"))) {
      return@withContext Result.failure(
        IllegalArgumentException("Enter a valid self-hosted LibreTranslate URL (http:// or https://).")
      )
    }

    val start = System.currentTimeMillis()
    return@withContext try {
      val payload = JSONObject().apply {
        put("q", text)
        put("source", source.code)
        put("target", target.code)
        put("format", "text")
      }
      val request = Request.Builder()
        .url("$cleanUrl/translate")
        .post(payload.toString().toRequestBody("application/json".toMediaType()))
        .build()

      client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          return@use Result.failure(
            IllegalStateException("LibreTranslate HTTP ${response.code}: $raw")
          )
        }
        val translated = JSONObject(raw).optString("translatedText")
        Result.success(
          TranslationResult(
            translatedText = translated,
            sourceLanguage = source,
            targetLanguage = target,
            engineName = "Self-Hosted LibreTranslate (AGPL-3.0)",
            isOffline = false,
            durationMs = System.currentTimeMillis() - start,
            statusNote = "User-configured endpoint: $cleanUrl",
          )
        )
      }
    } catch (t: Throwable) {
      Result.failure(t)
    }
  }
}
