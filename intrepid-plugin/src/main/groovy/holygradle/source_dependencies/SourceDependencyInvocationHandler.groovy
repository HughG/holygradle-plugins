package holygradle.source_dependencies

enum ExitCodeBehaviour {
    ignoreFailure,
    failImmediately,
    failAtEnd
}

class SourceDependencyInvocationHandler {
    public final Collection<String> cmdLine
    private ExitCodeBehaviour exitCodeBehaviour = ExitCodeBehaviour.failImmediately
    
    public SourceDependencyInvocationHandler(String... cmdLine) {
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

    public ExitCodeBehaviour getExitCodeBehaviour() {
        return exitCodeBehaviour
    }

    public String getDescription() {
        "'" + cmdLine.join(" ") + "'"
    }
}