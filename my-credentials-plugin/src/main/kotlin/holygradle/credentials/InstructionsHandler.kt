package holygradle.credentials

import org.gradle.api.Named

class InstructionsHandler(val _name: String): Named {
    val instructions = mutableListOf<String>()

    fun add(instruction: String) {
        instructions.add(instruction)
    }

    override fun getName(): String {
        return _name
    }
}