package holygradle

enum ExitCodeBehaviour {
    ignoreFailure,
    failImmediately,
    failAtEnd
}

class SourceDependencyInvocationHandler {
    public final def cmdLine
    public ExitCodeBehaviour exitCodeBehaviour = ExitCodeBehaviour.failImmediately
    
    public SourceDependencyInvocationHandler(def cmdLine) {
        this.cmdLine = cmdLine
    }
    
    public void ignoreFailure() {
        exitCodeBehaviour = ExitCodeBehaviour.ignoreFailure
    }
    
    public void failImmediately() {
        exitCodeBehaviour = ExitCodeBehaviour.failImmediately
    }
    
    public void failAtEnd() {
        exitCodeBehaviour = ExitCodeBehaviour.failAtEnd
    }
    
    public String getDescription() {
        "'" + cmdLine.join(" ") + "'"
    }
}