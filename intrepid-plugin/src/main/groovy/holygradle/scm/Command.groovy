package holygradle.scm

public interface Command {
   String execute(Closure configureExecSpec)
   String execute(Closure configureExecSpec, Closure throwForExitValue)
}