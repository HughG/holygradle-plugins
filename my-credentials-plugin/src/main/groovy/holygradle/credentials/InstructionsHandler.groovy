package holygradle.credentials

class InstructionsHandler {
    public final String name
    private final Collection<String> instructions = []
    
    public InstructionsHandler(String name) {
        this.name = name
    }
  
    public void add(String instruction) {
        instructions.add(instruction)
    }
    
    public Collection<String> getInstructions() {
        instructions
    }
}