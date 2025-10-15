package com.example.feedback

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FeedbackApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val baseUrl = "https://app.mspa.shop"
    
    fun submitFeedback(
        userId: String,
        feedback: String,
        images: List<File>? = null,
        callback: (Boolean, String) -> Unit
    ) {
        try {
            val formBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("id", userId)
                .addFormDataPart("time", getCurrentTime())
                .addFormDataPart("feedback", feedback)
            
            // 添加图片
            images?.forEach { imageFile ->
                if (imageFile.exists() && imageFile.length() > 0) {
                    val requestFile = imageFile.asRequestBody("image/*".toMediaType())
                    formBuilder.addFormDataPart(
                        "images",
                        imageFile.name,
                        requestFile
                    )
                }
            }
            
            val requestBody = formBuilder.build()
            
            val request = Request.Builder()
                .url("$baseUrl/api/feedback")
                .post(requestBody)
                .build()
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(false, "网络错误: ${e.message}")
                }
                
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""
                    
                    when {
                        response.isSuccessful -> {
                            callback(true, "反馈提交成功")
                        }
                        response.code == 400 -> {
                            callback(false, "请求参数错误")
                        }
                        response.code == 413 -> {
                            callback(false, "文件过大，请选择较小的图片")
                        }
                        response.code == 500 -> {
                            callback(false, "服务器内部错误")
                        }
                        response.code == 502 || response.code == 503 -> {
                            callback(false, "服务器暂时不可用")
                        }
                        response.code == 504 -> {
                            callback(false, "请求超时")
                        }
                        else -> {
                            callback(false, "提交失败: $responseBody")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            callback(false, "提交异常: ${e.message}")
        }
    }
    
    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }
    
    // 测试网络连接
    fun testConnection(callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "网络连接失败: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, "网络连接正常")
                } else {
                    callback(false, "服务器响应异常: ${response.code}")
                }
            }
        })
    }
}
