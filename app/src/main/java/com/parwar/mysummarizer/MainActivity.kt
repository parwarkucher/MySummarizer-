package com.parwar.mysummarizer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.parwar.mysummarizer.data.model.AIModel
import com.parwar.mysummarizer.data.model.ChatMessage
import com.parwar.mysummarizer.data.preferences.PreferencesManager
import com.parwar.mysummarizer.databinding.ActivityMainBinding
import com.parwar.mysummarizer.ui.ChatAdapter
import com.parwar.mysummarizer.ui.MainViewModel
import com.parwar.mysummarizer.ui.SummaryState
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.net.URL
import javax.inject.Inject

import android.content.DialogInterface
import androidx.core.content.ContextCompat
import com.parwar.mysummarizer.R

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var selectedModel: AIModel? = null
    private lateinit var markwon: Markwon
    private var currentState: SummaryState? = null
    private var chatAdapter: ChatAdapter? = null
    private var chatDialog: AlertDialog? = null
    
    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle shared text
        handleIntent(intent)

        // Observe token usage
        viewModel.lastTokenUsage.observe(this) { tokenUsage ->
            binding.tokenUsageText.apply {
                text = tokenUsage
                visibility = if (tokenUsage != null) View.VISIBLE else View.GONE
            }
        }

        setupMarkwon()
        setupModelSpinner()
        setupApiKeyInput()
        setupSummarizeButton()
        setupSummaryTypeButtons()
        setupChatButton()
        observeViewModel()

        binding.clearButton.setOnClickListener {
            binding.youtubeUrlInput.text?.clear()
            viewModel.clearAll()
            markwon.setMarkdown(binding.summaryText, "")
            binding.tokenUsageText.visibility = View.GONE
            Snackbar.make(binding.root, "All content cleared", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupMarkwon() {
        markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()
    }

    private fun setupModelSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            AIModel.models.map { AIModel.getDisplayText(it) }
        )

        (binding.modelSpinner as? AutoCompleteTextView)?.apply {
            setAdapter(adapter)
            setText(adapter.getItem(0), false)
            selectedModel = AIModel.models[0]
            
            setOnItemClickListener { _, _, position, _ ->
                selectedModel = AIModel.models[position]
                viewModel.setSelectedModel(AIModel.models[position].id)
                Log.d("ChatDebug", "Selected model: ${AIModel.models[position].id}")
            }
        }
    }

    private fun setupApiKeyInput() {
        // Load saved API key
        val savedApiKey = preferencesManager.apiKey
        if (savedApiKey.isNotEmpty()) {
            binding.openRouterApiKeyInput.setText(savedApiKey)
        }

        // Save API key when changed
        binding.openRouterApiKeyInput.doAfterTextChanged { text ->
            text?.toString()?.let { apiKey ->
                if (apiKey.isNotEmpty()) {
                    preferencesManager.apiKey = apiKey
                }
            }
        }
    }

    private fun setupSummaryTypeButtons() {
        binding.shortSummaryButton.setOnClickListener {
            binding.shortSummaryButton.isEnabled = false
            binding.detailedSummaryButton.isEnabled = true
            (currentState as? SummaryState.Success)?.let { state ->
                markwon.setMarkdown(binding.summaryText, state.shortSummary)
            }
        }

        binding.detailedSummaryButton.setOnClickListener {
            binding.shortSummaryButton.isEnabled = true
            binding.detailedSummaryButton.isEnabled = false
            (currentState as? SummaryState.Success)?.let { state ->
                markwon.setMarkdown(binding.summaryText, state.detailedSummary)
            }
        }
    }

    private fun setupChatButton() {
        binding.chatFab.setOnClickListener {
            showChatDialog()
        }
    }

    private fun showChatDialog() {
        if (chatDialog?.isShowing == true) {
            return
        }

        chatAdapter = chatAdapter ?: ChatAdapter()
        
        chatDialog = AlertDialog.Builder(this, R.style.ChatDialogStyle)
            .setView(R.layout.dialog_chat)
            .create()
            .apply {
                window?.attributes?.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }

        chatDialog?.show()

        val recyclerView = chatDialog?.findViewById<RecyclerView>(R.id.chatRecyclerView)
        recyclerView?.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        recyclerView?.adapter = chatAdapter

        val messageInput = chatDialog?.findViewById<TextInputEditText>(R.id.messageInput)
        val sendButton = chatDialog?.findViewById<MaterialButton>(R.id.sendButton)

        sendButton?.setOnClickListener {
            val message = messageInput?.text?.toString()
            if (!message.isNullOrBlank()) {
                Log.d("ChatDebug", "Sending message: $message")
                chatAdapter?.addMessage(ChatMessage(message, true))
                messageInput.text?.clear()
                
                // Check if model is selected
                if (selectedModel == null) {
                    Toast.makeText(this, "Please select an AI model first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Send message to AI model
                viewModel.sendChatMessage(message) { response ->
                    Log.d("ChatDebug", "Received response: $response")
                    runOnUiThread {
                        chatAdapter?.addMessage(ChatMessage(response, false))
                        // Scroll to the bottom after adding new message
                        recyclerView?.smoothScrollToPosition((chatAdapter?.itemCount ?: 1) - 1)
                    }
                }
            }
        }

        // Set up enter key to send message
        messageInput?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                sendButton?.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun setupSummarizeButton() {
        binding.summarizeButton.setOnClickListener {
            val url = binding.youtubeUrlInput.text.toString()
            val apiKey = binding.openRouterApiKeyInput.text.toString()
            val model = selectedModel

            when {
                url.isEmpty() -> {
                    Toast.makeText(this, "Please enter a YouTube URL", Toast.LENGTH_SHORT).show()
                }
                apiKey.isEmpty() -> {
                    Toast.makeText(this, "Please enter your OpenRouter API key", Toast.LENGTH_SHORT).show()
                }
                model == null -> {
                    Toast.makeText(this, "Please select a model", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    try {
                        val parsedUrl = URL(url)
                        if (!parsedUrl.host.contains("youtube.com") && !parsedUrl.host.contains("youtu.be")) {
                            Toast.makeText(this, "Please enter a valid YouTube URL", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        viewModel.processYouTubeUrl(url, model.id, apiKey)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Invalid URL format", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.summaryState.observe(this) { state ->
            currentState = state
            when (state) {
                is SummaryState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.summaryText.visibility = View.GONE
                    binding.summaryTypeButtons.visibility = View.GONE
                    binding.summaryText.text = ""
                }
                is SummaryState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.summaryText.visibility = View.GONE
                    binding.summaryTypeButtons.visibility = View.GONE
                    binding.summaryText.text = ""
                }
                is SummaryState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.summaryText.visibility = View.VISIBLE
                    binding.summaryTypeButtons.visibility = View.VISIBLE
                    
                    // Show short summary by default
                    binding.shortSummaryButton.isEnabled = false
                    binding.detailedSummaryButton.isEnabled = true
                    markwon.setMarkdown(binding.summaryText, state.shortSummary)
                }
                is SummaryState.Retrying -> {
                    // Show a special UI for retry state
                    binding.progressBar.visibility = View.VISIBLE
                    binding.summaryText.visibility = View.VISIBLE
                    binding.summaryTypeButtons.visibility = View.GONE
                    
                    // Build a message showing what's happening
                    val retryMessage = StringBuilder()
                    retryMessage.append("**Retry Attempt ${state.retryCount}/${6}**\n\n")
                    retryMessage.append("${state.message}\n\n")
                    
                    // Show what we're retrying
                    val retryingWhat = mutableListOf<String>()
                    if (state.retryingShort) retryingWhat.add("short summary")
                    if (state.retryingDetailed) retryingWhat.add("detailed summary")
                    retryMessage.append("Retrying: ${retryingWhat.joinToString(" and ")}\n\n")
                    
                    // If we have any successful summaries, show them
                    if (state.existingShortSummary != null || state.existingDetailedSummary != null) {
                        retryMessage.append("**Available Content:**\n\n")
                        
                        if (state.existingShortSummary != null) {
                            retryMessage.append("**Short Summary:**\n")
                            retryMessage.append("${state.existingShortSummary}\n\n")
                        }
                        
                        if (state.existingDetailedSummary != null) {
                            retryMessage.append("**Detailed Summary:**\n")
                            retryMessage.append("${state.existingDetailedSummary}\n\n")
                        }
                    }
                    
                    markwon.setMarkdown(binding.summaryText, retryMessage.toString())
                }
                is SummaryState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.summaryText.visibility = View.VISIBLE
                    binding.summaryTypeButtons.visibility = View.GONE
                    markwon.setMarkdown(binding.summaryText, "**Error:** ${state.message}")
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText?.contains("youtube.com") == true || sharedText?.contains("youtu.be") == true) {
                        binding.youtubeUrlInput.setText(sharedText.toString())
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                binding.youtubeUrlInput.text?.clear()
                viewModel.clearAll()
                markwon.setMarkdown(binding.summaryText, "")
                binding.tokenUsageText.visibility = View.GONE
                Snackbar.make(binding.root, "All content cleared", Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}