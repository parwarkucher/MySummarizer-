package com.parwar.mysummarizer.data.model

data class ModelPricing(
    val inputPrice: Double,  // Price per million tokens
    val outputPrice: Double  // Price per million tokens
)

data class AIModel(
    val name: String,
    val id: String,
    val provider: String,
    val contextLength: Int,
    val pricing: ModelPricing
) {
    companion object {
        val models = listOf(
            AIModel(
                name = "Gemini Flash 2.0 (free)",
                id = "google/gemini-2.0-flash-exp:free",
                provider = "Google",
                contextLength = 1050000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "Gemini 2.0 Flash Thinking",
                id = "google/gemini-2.0-flash-thinking-exp:free",
                provider = "Google",
                contextLength = 40000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "Llama 3.3 70B",
                id = "meta-llama/llama-3.3-70b-instruct",
                provider = "Novita AI (Meta)",
                contextLength = 131000,
                pricing = ModelPricing(0.39, 0.39)
            ),
            AIModel(
                name = "META 3.1 405B (free)",
                id = "meta-llama/llama-3.1-405b-instruct:free",
                provider = "Meta",
                contextLength = 8192,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "Claude 3.5 Sonnet",
                id = "anthropic/claude-3.5-sonnet",
                provider = "Anthropic",
                contextLength = 200000,
                pricing = ModelPricing(3.0, 15.0)
            ),
            AIModel(
                name = "DeepSeek V2.5",
                id = "deepseek/deepseek-chat",
                provider = "DeepSeek AI",
                contextLength = 65536,
                pricing = ModelPricing(0.15, 0.30)
            ),
            AIModel(
                name = "Gemini Experimental 1114",
                id = "google/gemini-exp-1114:free",
                provider = "Google",
                contextLength = 8192,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "GPT-4o",
                id = "openai/chatgpt-4o-latest",
                provider = "OpenAI",
                contextLength = 128000,
                pricing = ModelPricing(2.5, 10.0)
            ),
            AIModel(
                name = "META 3.1 70B (Free)",
                id = "meta-llama/llama-3.1-70b-instruct:free",
                provider = "Meta",
                contextLength = 8192,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "Claude 3.5 Haiku",
                id = "anthropic/claude-3.5-haiku",
                provider = "Anthropic",
                contextLength = 200000,
                pricing = ModelPricing(1.0, 5.0)
            ),
            AIModel(
                name = "Qwen 32B",
                id = "qwen/qwq-32b-preview",
                provider = "Qwen",
                contextLength = 33000,
                pricing = ModelPricing(1.2, 1.2)
            ),
            // New models below
            AIModel(
                name = "DeepSeek R1",
                id = "deepseek/deepseek-r1",
                provider = "DeepSeek AI",
                contextLength = 128000,
                pricing = ModelPricing(0.20, 0.60)
            ),
            AIModel(
                name = "Claude 3.7 Sonnet",
                id = "anthropic/claude-3.7-sonnet",
                provider = "Anthropic",
                contextLength = 200000,
                pricing = ModelPricing(3.0, 15.0)
            ),
            AIModel(
                name = "Llama 3.3 70B Instruct (free)",
                id = "meta-llama/llama-3.3-70b-instruct:free",
                provider = "Meta",
                contextLength = 131000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "Gemini Pro 2.0 Experimental (free)",
                id = "google/gemini-2.0-pro-exp-02-05:free",
                provider = "Google",
                contextLength = 2000000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "DeepSeek R1 (free)",
                id = "deepseek/deepseek-r1:free",
                provider = "DeepSeek AI",
                contextLength = 164000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "DeepSeek R1 Distill Llama 70B",
                id = "deepseek/deepseek-r1-distill-llama-70b",
                provider = "DeepSeek AI",
                contextLength = 131000,
                pricing = ModelPricing(0.25, 0.75)
            ),
            AIModel(
                name = "Gemini Flash 2.0 (pay)",
                id = "google/gemini-2.0-flash-001",
                provider = "Google",
                contextLength = 2000000,
                pricing = ModelPricing(0.35, 1.05)
            ),
            AIModel(
                name = "Claude 3.7 Sonnet (thinking)",
                id = "anthropic/claude-3.7-sonnet:thinking",
                provider = "Anthropic",
                contextLength = 200000,
                pricing = ModelPricing(3.0, 15.0)
            ),
            AIModel(
                name = "Gemini 2.0 Flash Thinking Experimental (free)",
                id = "google/gemini-2.0-flash-thinking-exp-1219:free",
                provider = "Google",
                contextLength = 40000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "Mistral Small 3 (0.9$)",
                id = "mistralai/mistral-small-24b-instruct-2501",
                provider = "Mistral AI",
                contextLength = 33000,
                pricing = ModelPricing(0.9, 0.9)
            ),
            AIModel(
                name = "DeepSeek V3 0324 (free)",
                id = "deepseek/deepseek-chat-v3-0324:free",
                provider = "DeepSeek AI",
                contextLength = 128000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "Gemini Pro 2.5 Experimental (free)",
                id = "google/gemini-2.5-pro-exp-03-25:free",
                provider = "Google",
                contextLength = 1000000,
                pricing = ModelPricing(0.0, 0.0)
            ),
            AIModel(
                name = "GPT-4o-mini(0.6$)",
                id = "openai/gpt-4o-mini",
                provider = "OpenAI",
                contextLength = 128000,
                pricing = ModelPricing(0.15, 0.6)
            ),
            AIModel(
                name = "Gemini Flash 1.5 (0.3$)",
                id = "google/gemini-flash-1.5",
                provider = "Google",
                contextLength = 1000000,
                pricing = ModelPricing(0.1, 0.3)
            ),
            AIModel(
                name = "Llama 3.1 8B Instruct (0.2$)",
                id = "meta-llama/llama-3.1-8b-instruct",
                provider = "Meta",
                contextLength = 131000,
                pricing = ModelPricing(0.2, 0.2)
            )
        )

        fun getDisplayText(model: AIModel): String {
            return model.name  // Just return the name without any additional info
        }
    }
}
