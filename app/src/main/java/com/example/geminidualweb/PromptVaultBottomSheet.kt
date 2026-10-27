package com.example.geminidualweb

import android.content.Context
import android.view.LayoutInflater
import com.example.geminidualweb.databinding.DialogPromptVaultBinding
import com.example.geminidualweb.databinding.ItemPromptTemplateBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

data class PromptTemplate(
    val title: String,
    val promptText: String
)

class PromptVaultBottomSheet(
    private val context: Context,
    private val onPromptSelected: (String) -> Unit
) {

    private val templates = listOf(
        PromptTemplate(
            title = "Principal Code Review",
            promptText = "Act as a Principal Software Engineer. Review the following code for concurrency bugs, edge cases, memory leaks, and architectural improvements:\n\n"
        ),
        PromptTemplate(
            title = "Executive Summary & Action Items",
            promptText = "Summarize the following text into a concise executive summary, key takeaways, and prioritized action items:\n\n"
        ),
        PromptTemplate(
            title = "First-Principles Deep Dive",
            promptText = "Explain the following concept from first principles. Avoid jargon where possible, provide concrete mental models, and highlight common misconceptions:\n\n"
        ),
        PromptTemplate(
            title = "System Design & Trade-offs",
            promptText = "Design a production-grade architecture for the following system. Cover API design, data modeling, scaling bottlenecks, failure modes, and key trade-offs:\n\n"
        ),
        PromptTemplate(
            title = "Root Cause Debugging",
            promptText = "Analyze the following error log / stack trace. Identify the root cause, explain why it happens, and provide a minimal, idiomatic fix:\n\n"
        )
    )

    fun show() {
        val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_GeminiDualWeb_BottomSheetDialog)
        val inflater = LayoutInflater.from(context)
        val binding = DialogPromptVaultBinding.inflate(inflater)

        templates.forEach { template ->
            val itemBinding = ItemPromptTemplateBinding.inflate(
                inflater,
                binding.promptListContainer,
                false
            )
            itemBinding.tvPromptLabel.text = template.title
            itemBinding.tvPromptPreview.text = template.promptText.trim()
            itemBinding.root.setOnClickListener {
                onPromptSelected(template.promptText)
                dialog.dismiss()
            }
            binding.promptListContainer.addView(itemBinding.root)
        }

        dialog.setContentView(binding.root)
        dialog.show()
    }
}
