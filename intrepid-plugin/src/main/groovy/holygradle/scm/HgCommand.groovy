package holygradle.scm

public interface HgCommand {
   String execute(Closure configureExecSpec);
}