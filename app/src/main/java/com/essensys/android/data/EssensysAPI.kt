package com.essensys.android.data

import android.util.Log
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object EssensysAPI {

    var localUrl: String = "http://mon.essensys.fr"
    var wanUrl: String = ""
    var username: String = ""
    var password: String = ""
    
    var isWanMode: Boolean = false
    var isDemoMode: Boolean = false // New Demo Mode flag
    var isConnected: Boolean = false
    
    // Dynamic property to get the correct URL
    var serverUrl: String
        get() = if (isWanMode) wanUrl else localUrl
        set(value) { /* No-op */ }

    // Init logic to check connection
    fun checkConnection(onResult: (Boolean) -> Unit) {
         if (isDemoMode) {
            isConnected = true
            onResult(true)
            return
        }

        val urlToCheck = serverUrl // Basic check on root or /api/serverinfos
        val request = Request.Builder()
            .url(if (urlToCheck.endsWith("/")) "${urlToCheck}api/serverinfos" else "${urlToCheck}/api/serverinfos") // Use a lightweight endpoint
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("EssensysAPI", "Connection check failed: ${e.message}")
                // Auto-switch to Demo Mode
                isDemoMode = true
                isConnected = true // Technically "connected" to demo
                onResult(true) // Return valid to app, but in demo mode
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (response.isSuccessful) {
                        isConnected = true
                         onResult(true)
                    } else {
                        Log.e("EssensysAPI", "Connection check HTTP error: ${response.code}")
                        isDemoMode = true
                        isConnected = true
                        onResult(true)
                    }
                }
            }
        })
    }
    
    // Simple callback interface
    interface ResultCallback {
        fun onSuccess()
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun sendInjection(k: Int, v: String, callback: ResultCallback) {
        val fullUrl = if (serverUrl.endsWith("/")) "${serverUrl}api/admin/inject" else "${serverUrl}/api/admin/inject"
        
        val json = JSONObject()
        json.put("k", k)
        json.put("v", v)
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .post(body)

        if (isWanMode && username.isNotEmpty() && password.isNotEmpty()) {
            val credential = Credentials.basic(username, password)
            requestBuilder.header("Authorization", credential)
        }

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("EssensysAPI", "Injection failed: ${e.message}")
                callback.onError(e.message ?: "Unknown network error")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("EssensysAPI", "Injection error: ${response.code}")
                        callback.onError("Server error: ${response.code}")
                    } else {
                        Log.d("EssensysAPI", "Injection success: $k -> $v")
                        callback.onSuccess()
                    }
                }
            }
        })
    }
}
