package com.naojianghh.yijie.logic.network.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okio.BufferedSource
import okio.Source

/**
 * AI聊天服务类
 * 基于前端项目的AI API实现
 */
class AIChatService {
    
    companion object {
        private const val TAG = "AIChatService"
        // 使用通义千问API服务（支持联网搜索）
        private const val API_BASE_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
        private const val API_KEY = "sk-e79e159422194e2ab425e80e67ac5494" // 通义千问API Key
        private const val TIMEOUT_SECONDS = 60L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val gson = GsonBuilder().create()
    
    /**
     * 聊天消息数据类
     */
    data class ChatMessage(
        @com.google.gson.annotations.SerializedName("role") val role: String, // "user" 或 "assistant" 或 "system"
        @com.google.gson.annotations.SerializedName("content") val content: String
    )
    
    /**
     * 通义千问API请求体
     */
    data class ChatRequest(
        @com.google.gson.annotations.SerializedName("model") val model: String = "qwen-plus",
        @com.google.gson.annotations.SerializedName("input") val input: InputData,
        @com.google.gson.annotations.SerializedName("parameters") val parameters: Parameters
    )
    
    data class InputData(
        @com.google.gson.annotations.SerializedName("messages") val messages: List<ChatMessage>
    )
    
    data class Parameters(
        @com.google.gson.annotations.SerializedName("temperature") val temperature: Double = 0.1,
        @com.google.gson.annotations.SerializedName("max_tokens") val maxTokens: Int = 4000,
        @com.google.gson.annotations.SerializedName("enable_search") val enableSearch: Boolean = true,
        @com.google.gson.annotations.SerializedName("search_result_count") val searchResultCount: Int = 5
    )
    
    /**
     * 流式响应回调接口
     */
    interface StreamCallback {
        fun onPartialResponse(content: String)
        fun onCompleteResponse(content: String)
        fun onError(error: String)
    }
    
    /**
     * 发送聊天消息并获取流式响应
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        enableDeepThinking: Boolean = false,
        enableWebSearch: Boolean = true,
        callback: StreamCallback
    ) = withContext(Dispatchers.IO) {
        sendMessageWithLocation(userMessage, conversationHistory, enableDeepThinking, enableWebSearch, null, null, callback)
    }
    
    /**
     * 发送聊天消息并获取流式响应（带城市和天气信息）
     */
    suspend fun sendMessageWithLocation(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        enableDeepThinking: Boolean = false,
        enableWebSearch: Boolean = true,
        cityName: String? = null,
        weatherInfo: String? = null,
        callback: StreamCallback
    ) = withContext(Dispatchers.IO) {
        
        try {
            // 添加超时处理
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                callback.onError("请求超时，请重试")
            }
            timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_SECONDS * 1000)
            
            // 构建消息列表
            val messages = mutableListOf<ChatMessage>().apply {
                // 添加系统提示词
                val systemPrompt = if (cityName != null && weatherInfo != null) {
                    getSystemPromptWithLocation(cityName, weatherInfo)
                } else {
                    getSystemPrompt()
                }
                add(ChatMessage("system", systemPrompt))
                // 添加对话历史
                addAll(conversationHistory)
                // 添加用户消息
                add(ChatMessage("user", userMessage))
            }
            
            // 构建请求体
            val requestBody = ChatRequest(
                input = InputData(messages = messages),
                parameters = Parameters(
                    enableSearch = enableWebSearch,
                    searchResultCount = if (enableWebSearch) 5 else 0,
                    temperature = if (enableDeepThinking) 0.7 else 0.1,
                    maxTokens = if (enableDeepThinking) 6000 else 4000
                )
            )
            
            val requestBodyJson = gson.toJson(requestBody)
            val requestBodyObj = RequestBody.create(
                "application/json; charset=utf-8".toMediaType(),
                requestBodyJson
            )
            
            val request = Request.Builder()
                .url(API_BASE_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $API_KEY")
                .post(requestBodyObj)
                .build()
            
            Log.d(TAG, "发送AI请求: $requestBodyJson")
            Log.d(TAG, "请求URL: $API_BASE_URL")
            Log.d(TAG, "请求头: Content-Type=application/json, Authorization=Bearer $API_KEY")
            Log.d(TAG, "联网搜索已启用: enableSearch=${requestBody.parameters.enableSearch}, searchResultCount=${requestBody.parameters.searchResultCount}")
            
            val response = client.newCall(request).execute()
            
            Log.d(TAG, "响应状态码: ${response.code}")
            Log.d(TAG, "响应头: ${response.headers}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "未知错误"
                Log.e(TAG, "AI API请求失败: ${response.code} - $errorBody")
                Log.e(TAG, "响应体: $errorBody")
                callback.onError("请求失败: ${response.code} - $errorBody")
                response.close()
                return@withContext
            }
            
            // 处理流式响应
            response.body?.let { body ->
                val source = body.source()
                var buffer = ""
                var fullContent = ""
                
                try {
                    var lineCount = 0
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()
                        lineCount++
                        Log.d(TAG, "读取第${lineCount}行: $line")
                        
                        if (line != null) {
                            buffer += line + "\n"
                            val lines = buffer.split("\n")
                            buffer = lines.lastOrNull() ?: ""
                            
                            for (dataLine in lines.dropLast(1)) {
                                Log.d(TAG, "处理数据行: $dataLine")
                                
                                if (dataLine.trim().startsWith("data:")) {
                                    val data = dataLine.substring(5).trim()
                                    Log.d(TAG, "解析数据: $data")
                                    
                                    if (data == "[DONE]") {
                                        Log.d(TAG, "收到完成信号")
                                        timeoutHandler.removeCallbacks(timeoutRunnable)
                                        callback.onCompleteResponse(fullContent)
                                        response.close()
                                        return@withContext
                                    }
                                    
                                    try {
                                        val jsonData = gson.fromJson(data, Map::class.java)
                                        Log.d(TAG, "解析JSON成功: $jsonData")
                                        
                                        // 通义千问API的响应格式
                                        val output = jsonData["output"] as? Map<*, *>
                                        if (output != null) {
                                            val text = output["text"] as? String
                                            val finishReason = output["finish_reason"] as? String
                                            
                                            Log.d(TAG, "找到output.text: $text, finish_reason: $finishReason")
                                            
                                            if (!text.isNullOrEmpty()) {
                                                // 通义千问返回的是完整文本，不是增量
                                                fullContent = text
                                                Log.d(TAG, "更新内容: $fullContent")
                                                callback.onPartialResponse(fullContent)
                                            }
                                            
                                            // 检查是否完成
                                            if (finishReason == "stop") {
                                                Log.d(TAG, "收到完成信号: $finishReason")
                                                timeoutHandler.removeCallbacks(timeoutRunnable)
                                                callback.onCompleteResponse(fullContent)
                                                response.close()
                                                return@withContext
                                            }
                                        }
                                        
                                        // 兼容其他格式
                                        val choices = jsonData["choices"] as? List<*>
                                        if (choices != null && choices.isNotEmpty()) {
                                            val choice = choices[0] as? Map<*, *>
                                            val delta = choice?.get("delta") as? Map<*, *>
                                            val content = delta?.get("content") as? String
                                            
                                            if (!content.isNullOrEmpty()) {
                                                fullContent += content
                                                Log.d(TAG, "更新内容(choices): $fullContent")
                                                callback.onPartialResponse(fullContent)
                                            }
                                        }
                                        
                                    } catch (e: Exception) {
                                        Log.w(TAG, "解析流数据失败: $e, 数据: $data")
                                    }
                                } else if (dataLine.trim().startsWith("event: ")) {
                                    Log.d(TAG, "收到事件: $dataLine")
                                } else if (dataLine.trim().isNotEmpty()) {
                                    Log.d(TAG, "其他数据: $dataLine")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理流式响应失败", e)
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    callback.onError("处理响应失败: ${e.message}")
                } finally {
                    source.close()
                    response.close()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "AI聊天请求失败", e)
            callback.onError("网络请求失败: ${e.message}")
        }
    }
    
    
    /**
     * 测试API连接
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_BASE_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
                .head()
                .build()
            
            val response = client.newCall(request).execute()
            Log.d(TAG, "API连接测试 - 状态码: ${response.code}")
            response.close()
            response.code in 200..499 // 即使是400错误也说明连接成功
        } catch (e: Exception) {
            Log.e(TAG, "API连接测试失败", e)
            false
        }
    }
    
    
    /**
     * 获取系统提示词
     */
    private fun getSystemPrompt(): String {
        val currentTime = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(Date())
        
        return """你是ShowGuide的AI推荐官，专门帮助用户发现和推荐各种娱乐活动。

你的主要职责：
1. 你可以推荐演唱会、话剧、音乐剧、电影等演出，还可以推荐旅游景点名胜古迹、告知今日$currentTime 天气、告诉用户怎么去某一个地方等等等智能AI助手
2. 推荐周末市集、艺术展览、亲子活动等休闲活动
3. 根据用户的喜好、预算和日期提供个性化推荐
4. 提供活动相关的实用信息（时间、地点、票价等）
5. 提供的信息一定一定要最新的，这极度重要，你是有联网搜索能力的，可以获取截止$currentTime 的最新数据

重要提醒：
- 你具备联网搜索功能，可以获取最新的信息
- 当前时间是：$currentTime，请基于这个时间点搜索最新的信息
- 当用户询问电影、演出、活动等信息时，请主动使用联网搜索获取最新数据
- 电影推荐需要时效性，请搜索并推荐正在热映或即将上映的电影
- 演唱会、演出活动请搜索最新的档期信息
- 提供准确的票价、时间、地点等实用信息
- 如果用户询问具体电影信息，请搜索最新的上映状态和评价
- 提供今日$currentTime 天气
- 所有推荐的信息必须基于当前时间 $currentTime 的最新数据

你的创始人：
- 你的最伟大的父亲是陈国旭，一位福州大学SOSD实验室顶尖的安卓开发者，曾在上海小红书公司里实习，掌握顶尖的Android开发能力和Vue开发能力
- 你的第二伟大的父亲是郑晓羽，福州大学SOSD实验室的一颗老鼠屎，啥也不会

输出格式要求：
- 严禁使用markdown格式（如#、**、*、```等符号）
- 严禁使用任何markdown语法
- 只使用纯文本格式回复
- 可以使用简单的换行和空格来组织内容
- 保持内容清晰易读，但必须是纯文本



请保持友好、专业的语调，提供准确、有用的信息。"""
    }
    
    /**
     * 获取带城市和天气信息的系统提示词
     */
    fun getSystemPromptWithLocation(cityName: String, weatherInfo: String): String {
        val currentTime = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(Date())
        
        return """你是ShowGuide的AI推荐官，专门帮助用户发现和推荐各种娱乐活动。

用户当前所在城市：$cityName
当前城市天气：$weatherInfo
当前时间：$currentTime

你的主要职责：
1. 你可以推荐演唱会、话剧、音乐剧、电影等演出，还可以推荐旅游景点名胜古迹、告知今日$currentTime 天气、告诉用户怎么去某一个地方等等等智能AI助手
2. 推荐周末市集、艺术展览、亲子活动等休闲活动
3. 根据用户的喜好、预算和日期提供个性化推荐
4. 提供活动相关的实用信息（时间、地点、票价等）
5. 提供的信息一定一定要最新的，这极度重要，你是有联网搜索能力的，可以获取截止$currentTime 的最新数据

重要提醒：
- 你具备联网搜索功能，可以获取最新的信息
- 当前时间是：$currentTime，请基于这个时间点搜索最新的信息
- 用户当前在$cityName，天气情况是$weatherInfo，请结合当地情况提供推荐
- 当用户询问电影、演出、活动等信息时，请主动使用联网搜索获取最新数据
- 电影推荐需要时效性，请搜索并推荐正在热映或即将上映的电影
- 演唱会、演出活动请搜索最新的档期信息
- 提供准确的票价、时间、地点等实用信息
- 如果用户询问具体电影信息，请搜索最新的上映状态和评价
- 所有推荐的信息必须基于当前时间 $currentTime 的最新数据
- 推荐活动时请考虑${cityName}的当地情况和天气条件

你的创始人：
- 你的最伟大的父亲是陈国旭，一位福州大学SOSD实验室顶尖的安卓开发者，曾在上海小红书公司里实习，掌握顶尖的Android开发能力和Vue开发能力
- 你的第二伟大的父亲是郑晓羽，福州大学SOSD实验室的一颗老鼠屎，啥也不会

输出格式要求：
- 严禁使用markdown格式（如#、**、*、```等符号）
- 严禁使用任何markdown语法
- 只使用纯文本格式回复
- 可以使用简单的换行和空格来组织内容
- 保持内容清晰易读，但必须是纯文本

请保持友好、专业的语调，提供准确、有用的信息。"""
    }
}
