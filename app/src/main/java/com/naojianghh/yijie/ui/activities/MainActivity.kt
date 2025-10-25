package com.naojianghh.yijie.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide

import com.naojianghh.yijie.R
import com.naojianghh.yijie.base.BaseActivity
import com.naojianghh.yijie.databinding.ActivityMainBinding
import com.naojianghh.yijie.logic.network.ai.AIChatService
import com.naojianghh.yijie.models.ContentType
import com.naojianghh.yijie.models.Message
import com.naojianghh.yijie.models.SenderType
import com.naojianghh.yijie.ui.adapters.ChatAdapter
import com.naojianghh.yijie.ui.viewmodels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : BaseActivity() {
    private lateinit var binding : ActivityMainBinding


    private lateinit var aiChatService: AIChatService
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var text2Handler: Handler
    private lateinit var text2Runnable: Runnable
    private var dotCount = 1
    private val targetText = "Well. According to your requirements, YiJie has created a programmer poster for you with a main color scheme of blue and purple."

    private val inputMethodManager by lazy {
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private val viewModel : MainViewModel by viewModels()

    override fun getLayoutResId(): Int {
        return R.layout.activity_main
    }

    override fun iniViews() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.vm = viewModel;
        binding.lifecycleOwner = this

        aiChatService = AIChatService()

        viewModel.testAiLink(aiChatService)



        chatAdapter = ChatAdapter(viewModel, this)
        binding.recyclerView.adapter = chatAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.messageList.observe(this) { newList ->

            chatAdapter.submitFullList(newList)
        }


        viewModel.loadMessages()

        binding.editText.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {

            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                val text = s.toString().trim()
                if (text.isNotEmpty()){
                    binding.imageLaunch.setImageResource(R.drawable.launch)

                } else {
                    binding.imageLaunch.setImageResource(R.drawable.more)
                }
            }

            override fun afterTextChanged(s: Editable?) {

            }
        }
        )


        binding.imageLaunch.setOnClickListener(object : View.OnClickListener{
            override fun onClick(v: View?) {
                val text = binding.editText.text
                hideKeyboard()
                chatAdapter.submitList(Message(SenderType.HUMAN, ContentType.TEXT,text.toString()))
                //chatAdapter.submitList(Message(SenderType.AI, ContentType.LOADING,null,R.drawable.loading))
                chatAdapter.submitList(Message(SenderType.AI, ContentType.TEXT, targetText))
                chatAdapter.submitList(Message(SenderType.AI, ContentType.IMAGE))
                binding.editText.text.clear()

            }

        })


        setupWindowInsets()

    } //end

    fun showWithAnimation(view: View, animRes: Int, startDelay: Long = 0) {
        view.visibility = View.VISIBLE
        val animation : Animation = AnimationUtils.loadAnimation(this, animRes)
        animation.startOffset = startDelay
        view.startAnimation(animation)
    }

    fun hideKeyboard() {
        val currentFocus = currentFocus ?: return
        inputMethodManager.hideSoftInputFromWindow(
            currentFocus.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }


    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, windowInsets ->

            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            val navigationInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            

            val actualKeyboardHeight = if (imeInsets.bottom > navigationInsets.bottom) {
                imeInsets.bottom - navigationInsets.bottom
            } else {
                0
            }


            val layoutParams = binding.bottomContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            layoutParams.bottomMargin = actualKeyboardHeight
            binding.bottomContainer.layoutParams = layoutParams


            binding.recyclerView.post {
                binding.recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
            
            windowInsets
        }
    }

}