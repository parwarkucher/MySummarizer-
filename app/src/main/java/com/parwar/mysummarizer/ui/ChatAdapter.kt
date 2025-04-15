package com.parwar.mysummarizer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.parwar.mysummarizer.R
import com.parwar.mysummarizer.data.model.ChatMessage
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()
    private var markwon: Markwon? = null

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        markwon = Markwon.builder(recyclerView.context)
            .usePlugin(TablePlugin.create(recyclerView.context))
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount() = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)

        fun bind(message: ChatMessage) {
            if (message.isFromUser) {
                messageContainer.setBackgroundResource(R.drawable.bg_user_message)
                messageText.text = message.content
            } else {
                messageContainer.setBackgroundResource(R.drawable.bg_ai_message)
                // Use Markwon for AI messages
                markwon?.setMarkdown(messageText, message.content)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }
}
