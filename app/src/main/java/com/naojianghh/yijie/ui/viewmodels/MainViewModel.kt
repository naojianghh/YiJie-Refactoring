package com.naojianghh.yijie.ui.viewmodels

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.naojianghh.yijie.logic.network.ai.AIChatService
import com.naojianghh.yijie.models.ContentType
import com.naojianghh.yijie.models.Message
import com.naojianghh.yijie.models.SenderType
import com.naojianghh.yijie.ui.activities.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.collections.mutableListOf

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _text1 = MutableLiveData<String>("Hello, I'm Yijie AI assistant. Please tell me what kind of interface design you are looking for, and I will do my best to assist you.")
    val text1 : LiveData<String> = _text1

    private val _text3 = MutableLiveData<String>("I would like a poster with the theme of \"Programmers\", and the colors should be mainly blue and purple.")
    val text3 : LiveData<String> = _text3

    private val _typingState = MutableStateFlow("")
    val typingState: StateFlow<String> = _typingState.asStateFlow()

    private var isTyping = false

    fun startTyping(targetText: String) {
        if (isTyping) return
        viewModelScope.launch {
            isTyping = true
            targetText.forEachIndexed { index, _ ->
                _typingState.value = targetText.substring(0, index + 1)
                delay(30)
            }
            isTyping = false
        }
    }

    private val _messageList = MutableLiveData<MutableList<Message>>()
    val messageList: LiveData<MutableList<Message>> = _messageList


    fun loadMessages() {
        val mockData = mutableListOf(
            Message(SenderType.AI, ContentType.TEXT,"Hello, I'm Yijie AI assistant. Please tell me what kind of interface design you are looking for, and I will do my best to assist you.",null,true),
        )
        _messageList.postValue(mockData)
    }

    fun addMessage(newMessage: Message) {
        val currentList = _messageList.value ?: mutableListOf()
        val updatedList = currentList.toMutableList()
        updatedList.add(newMessage)
        _messageList.postValue(updatedList)
    }

    fun testAiLink(aiChatService : AIChatService){
        CoroutineScope(Dispatchers.Main).launch {
            val isConnected = aiChatService.testConnection()
            if (isConnected) {
                android.util.Log.d("AIActivity", "AI服务连接正常")
            } else {
                android.util.Log.e("AIActivity", "AI服务连接失败")
            }
        }
    }
}