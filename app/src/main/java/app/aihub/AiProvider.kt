package app.aihub

data class AiProvider(
    val id: String,
    val name: String,
    val region: String,
    val url: String,
    val description: String
)

object Providers {
    val all = listOf(
        AiProvider("deepseek", "DeepSeek", "中国 · 国际入口", "https://chat.deepseek.com/", "通用对话与推理"),
        AiProvider("qwen", "Qwen", "中国 · 国际入口", "https://chat.qwen.ai/", "通用与多模态"),
        AiProvider("glm", "GLM / Z.ai", "中国 · 国际入口", "https://chat.z.ai/", "通用与编程"),
        AiProvider("kimi", "Kimi", "中国 · 国际入口", "https://www.kimi.ai/", "长文本与研究"),
        AiProvider("minimax", "MiniMax", "中国 · 国际入口", "https://www.minimax.io/", "多模态与智能体"),
        AiProvider("ernie", "ERNIE", "中国 · 官方入口", "https://yiyan.baidu.com/", "知识增强与通用对话"),
        AiProvider("chatgpt", "ChatGPT", "美国", "https://chatgpt.com/", "OpenAI"),
        AiProvider("claude", "Claude", "美国", "https://claude.ai/", "Anthropic"),
        AiProvider("gemini", "Gemini", "美国", "https://gemini.google.com/", "Google")
    )
}
