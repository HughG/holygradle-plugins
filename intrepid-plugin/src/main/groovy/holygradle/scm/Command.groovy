package holygradle.scm

public interface Command {
   String execute(Closure configureExecSpec);
}