package com.yshs.aicommit.pojo

import com.yshs.aicommit.util.PromptUtil
import java.io.Serializable

data class PromptInfo(
    var description: String = "",
    var prompt: String = "",
) : Serializable {

    override fun toString(): String = description

    companion object {
        @JvmStatic
        fun defaultPrompts(): MutableList<PromptInfo> =
            mutableListOf(
                PromptInfo("Default", PromptUtil.DEFAULT_PROMPT_1),
                PromptInfo("Detailed", PromptUtil.DEFAULT_PROMPT_2),
                PromptInfo("Perfect", PromptUtil.DEFAULT_PROMPT_3),
                PromptInfo("EMOJI", PromptUtil.EMOJI),
                PromptInfo("Conventional", PromptUtil.CONVENTIONAL),
            )
    }
}
