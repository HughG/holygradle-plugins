package holygradle.credentials

import org.gradle.*
import org.gradle.api.*

class InstructionsHandler {
    public final String name
    private def instructions = []
    
    public InstructionsHandler(String name) {
        this.name = name
    }
  
    public void add(String instruction) {
        instructions.add(instruction)
    }
    
    public String[] getInstructions() {
        instructions as String[]
    }
}