package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LocalDream 本地生图 API（手机 NPU 跑 Stable Diffusion）
 * 协议：POST {host}/generate → SSE 流（complete 事件带 base64 图片）
 */
object LocalDreamApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        prompt: String,
        host: String = "http://127.0.0.1:8081",
        negative: String = "lowres, bad anatomy, worst quality",
        steps: Int = 20,
        width: Int = 512,
        height: Int = 768,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("prompt", prompt)
            put("negative_prompt", negative)
            put("steps", steps)
            put("cfg", 7)
            put("seed", kotlin.random.Random.nextInt(1000000000))
            put("width", width)
            put("height", height)
            put("output_format", "jpeg")
        }.toString()

        val req = Request.Builder()
            .url("$host/generate")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("LocalDream 生图失败 HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            // 解析 SSE 流中的 complete 事件，取 base64 图片
            val complete = Regex("""\"type\":\s*\"complete\"[\s\S]*?\"image\":\s*\"([^\"]+)\"""").find(text)
                ?: throw Exception("未收到生成结果（确认 LocalDream 已开启受控模式并加载模型）")
            complete.groupValues[1]
        }
    }
}
