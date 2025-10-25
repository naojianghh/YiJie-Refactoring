package com.naojianghh.yijie.ui.adapters

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.bumptech.glide.Glide
import com.naojianghh.yijie.databinding.ItemAiLoadingBinding
import com.naojianghh.yijie.databinding.ItemAiPictureBinding
import com.naojianghh.yijie.databinding.ItemAiTextBinding
import com.naojianghh.yijie.databinding.ItemPersonPictureBinding
import com.naojianghh.yijie.databinding.ItemPersonTextBinding
import com.naojianghh.yijie.models.ContentType
import com.naojianghh.yijie.models.Message
import com.naojianghh.yijie.models.SenderType
import com.naojianghh.yijie.ui.activities.MainActivity
import com.naojianghh.yijie.ui.activities.PictureDetailActivity
import com.naojianghh.yijie.ui.viewmodels.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.naojianghh.yijie.R
import kotlinx.coroutines.withTimeoutOrNull

class ChatAdapter(
    private val parentViewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner,
) : Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_TEXT_PERSON: Int = 0
    private val TYPE_TEXT_AI: Int = 1
    private val TYPE_PICTURE_PERSON: Int = 2
    private val TYPE_PICTURE_AI: Int = 3
    private val TYPE_LOADING_AI: Int = 4

    private var messageList: MutableList<Message> = mutableListOf()

    fun submitFullList(newList: MutableList<Message>){
        messageList = newList
        notifyDataSetChanged()
    }

    fun submitList(newItem: Message) {
        messageList.add(newItem)
        notifyDataSetChanged()
    }

    fun deleteList(position: Int) {
        messageList.removeAt(position)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT_AI -> {
                val binding = ItemAiTextBinding.inflate(inflater, parent, false)
                AiTextViewHolder(binding,parentViewModel,this)
            }
            TYPE_TEXT_PERSON -> {
                val binding = ItemPersonTextBinding.inflate(inflater, parent, false)
                PersonTextViewHolder(binding)
            }
            TYPE_PICTURE_AI -> {
                val binding = ItemAiPictureBinding.inflate(inflater, parent, false)
                AiPictureViewHolder(binding)
            }
            TYPE_PICTURE_PERSON -> {
                val binding = ItemPersonPictureBinding.inflate(inflater, parent, false)
                PersonPictureViewHolder(binding)
            }
            TYPE_LOADING_AI -> {
                val binding = ItemAiLoadingBinding.inflate(inflater,parent,false)
                AiLoadingViewHolder(binding,lifecycleOwner,parentViewModel,this)
            }
            else -> {
                throw IllegalArgumentException("未知布局类型")
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
       val item = messageList[position]
        when (holder){
            is PersonTextViewHolder -> {
                holder.binding.apply {
                    itemData = item
                    parentVm = parentViewModel
                    lifecycleOwner = this@ChatAdapter.lifecycleOwner
                    executePendingBindings()
                }
            }
            is AiTextViewHolder -> {
                holder.binding.apply {
                    itemData = item
                    parentVm = parentViewModel
                    lifecycleOwner = this@ChatAdapter.lifecycleOwner
                    executePendingBindings()
                }
                holder.binding.text.visibility = View.GONE
                holder.startLoading()

            }
            is AiPictureViewHolder -> {
                holder.binding.apply {
                    itemData = item
                    parentVm = parentViewModel
                    lifecycleOwner = this@ChatAdapter.lifecycleOwner
                    executePendingBindings()
                }
                holder.binding.text.setOnClickListener(object : View.OnClickListener{
                    override fun onClick(v: View?) {
                        val context = holder.itemView.context
                        var intent : Intent = Intent(context, PictureDetailActivity::class.java)
                        context.startActivity(intent)
                    }

                })
                Glide.with(holder.itemView.context)
                    .asGif()
                    .load(R.drawable.loading)
                    .into(holder.binding.loading)
                if (holder.binding.itemData?.isTyped != true) {
                    holder.binding.text.visibility = View.GONE
                    holder.startLoading()
                    holder.binding.itemData?.isTyped = true
                }
            }
            is PersonPictureViewHolder -> {
                holder.binding.apply {
                    itemData = item
                    parentVm = parentViewModel
                    lifecycleOwner = this@ChatAdapter.lifecycleOwner
                    executePendingBindings()
                }
            }
            is AiLoadingViewHolder -> {
                holder.binding.apply {
                    itemData = item
                    parentVm = parentViewModel
                    lifecycleOwner = this@ChatAdapter.lifecycleOwner
                    executePendingBindings()
                }
                Glide.with(holder.itemView.context)
                    .asGif()
                    .load(holder.binding.itemData?.sourceId ?: 0)
                    .into(holder.binding.image)

                holder.startLoading()
                holder.deleteItem()

            }
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    override fun getItemViewType(position: Int): Int {
        val message: Message = messageList[position]
        return when (message.senderType) {
            SenderType.AI -> {
                when (message.contentType) {
                    ContentType.TEXT -> {
                        TYPE_TEXT_AI
                    }
                    ContentType.IMAGE -> {
                        TYPE_PICTURE_AI
                    }
                    ContentType.LOADING -> {
                        TYPE_LOADING_AI
                    }
                }
            }
            SenderType.HUMAN -> {
                when (message.contentType) {
                    ContentType.TEXT -> {
                        TYPE_TEXT_PERSON
                    }
                    ContentType.IMAGE -> {
                        TYPE_PICTURE_PERSON
                    }

                    ContentType.LOADING -> {
                        TYPE_LOADING_AI
                    }
                }
            }
            else -> throw IllegalArgumentException("未知类型")
        }
    }

    inner class PersonTextViewHolder(val binding : ItemPersonTextBinding) : RecyclerView.ViewHolder(binding.root) {

    }

    inner class AiTextViewHolder(val binding : ItemAiTextBinding,

                                 private val viewModel: MainViewModel,
                                 private val adapter: ChatAdapter,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun startTyping(targetText: String) {
            val currentMessage = adapter.messageList[adapterPosition]

                lifecycleOwner.lifecycleScope.launch {
                    targetText.forEachIndexed { index, _ ->
                        binding.text.text = targetText.substring(0, index + 1)
                        delay(30)
                    }
                    currentMessage.isTyped = true
                }

        }

        fun startLoading() {
            val currentMessage = adapter.messageList[adapterPosition]
            if (!currentMessage.isTyped && currentMessage.contentType == ContentType.TEXT) {
                binding.loading.visibility = View.VISIBLE
                lifecycleOwner.lifecycleScope.launch {
                    var dotCount: Int = 1
                    withTimeoutOrNull(5000) {
                        while (true) {
                            binding.loading.text = when (dotCount) {
                                1 -> "Thinking."
                                2 -> "Thinking.."
                                3 -> "Thinking..."
                                else -> "Thinking."
                            }
                            dotCount = (dotCount % 3) + 1
                            delay(500)

                        }
                    }
                    binding.loading.visibility = View.GONE
                    binding.text.visibility = View.VISIBLE
                    startTyping(binding.itemData?.content ?: "")
                }
            }
            else {
                binding.text.visibility = View.VISIBLE
                binding.text.text = currentMessage.content
            }
        }
    }

    inner class AiPictureViewHolder(val binding : ItemAiPictureBinding) : RecyclerView.ViewHolder(binding.root) {
        fun startLoading() {
            binding.loading.visibility = View.VISIBLE
            lifecycleOwner.lifecycleScope.launch{
                delay(7000)
                binding.loading.visibility = View.GONE
                binding.text.visibility = View.VISIBLE
            }
        }
    }

    inner class PersonPictureViewHolder(val binding : ItemPersonPictureBinding) : RecyclerView.ViewHolder(binding.root) {

    }

    inner class AiLoadingViewHolder(val binding : ItemAiLoadingBinding,
                                    private val lifecycleOwner: LifecycleOwner,
                                    private val viewModel: MainViewModel,
                                    private val adapter: ChatAdapter,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var dotCount = 1

        private var thinkingJob: Job? = null
        private var deleteJob: Job? = null


        fun startLoading() {
            thinkingJob?.cancel()

            thinkingJob = lifecycleOwner.lifecycleScope.launch {
                while (true) {
                    binding.text.text = when (dotCount) {
                        1 -> "Thinking."
                        2 -> "Thinking.."
                        3 -> "Thinking..."
                        else -> "Thinking."
                    }
                    dotCount = (dotCount % 3) + 1
                    delay(500)
                }
            }
        }


        fun deleteItem() {

            deleteJob?.cancel()

            deleteJob = lifecycleOwner.lifecycleScope.launch {
                delay(7000)

                cancelAllCoroutines()

                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    adapter.deleteList(currentPosition)
                }
            }
        }

        fun cancelAllCoroutines() {
            thinkingJob?.cancel()
            deleteJob?.cancel()
            thinkingJob = null
            deleteJob = null
        }

    }


}