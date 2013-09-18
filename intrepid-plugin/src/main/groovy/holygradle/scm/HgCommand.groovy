package holygradle.scm

public interface HgCommand {
   String execute(Collection<String> args);
}