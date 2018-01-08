package holygradle.scm

import holygradle.testUtil.ExecUtil
import org.gradle.process.ExecSpec

class StubCommand implements Command {
    private final String name
    private final List<Effect> effects
    private final Closure doExec

    StubCommand(String name, List<Effect> effects, Closure doExec) {
        this.name = name
        this.effects = effects
        this.doExec = doExec
    }

    StubCommand(String name, List<Effect> effects) {
        this(name, effects, {[0, ""]})
    }

    @Override
    String execute(Closure configureExecSpec) {
        ExecSpec execSpec = ExecUtil.makeStubExecSpec().<ExecSpec> with(configureExecSpec)
        def (int exitValue, String output) = doExec(execSpec)
        effects.add(new Effect(execSpec, null))
        if (exitValue != 0) { throw new RuntimeException("Exit value was ${exitValue}") }
        return output
    }

    @Override
    String execute(Closure configureExecSpec, Closure throwForExitValue) {
        ExecSpec execSpec = ExecUtil.makeStubExecSpec().<ExecSpec> with(configureExecSpec)
        def (int exitValue, String output) = doExec(execSpec)
        def shouldThrow = (exitValue != 0) && throwForExitValue(exitValue)
        effects.add(new Effect(execSpec, shouldThrow))
        if (shouldThrow) { throw new RuntimeException("Exit value was ${exitValue}") }
        return output
    }

    Effect makeEffect(List<String> args, File workingDir, Boolean shouldThrow) {
        return new Effect(args, workingDir, shouldThrow)
    }

    class Effect {
        public final List<String> args
        public final File workingDir
        public final Boolean shouldThrow

        Effect(List<String> args, File workingDir, Boolean shouldThrow) {
            this.args = args
            this.workingDir = workingDir
            this.shouldThrow = shouldThrow
        }

        Effect(ExecSpec execSpec, Boolean shouldThrow) {
            this.args = execSpec.args
            this.workingDir = execSpec.workingDir
            this.shouldThrow = shouldThrow
        }

        @Override
        public String toString() {
            return "Effect{" +
                "for=" + name +
                ", args=" + args +
                ", workingDir=" + workingDir +
                ", shouldThrow=" + shouldThrow +
                '}'
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Effect effect = (Effect) o

            if (args != effect.args) return false
            if (shouldThrow != effect.shouldThrow) return false
            if (workingDir != effect.workingDir) return false

            return true
        }

        int hashCode() {
            int result
            result = args.hashCode()
            result = 31 * result + workingDir.hashCode()
            result = 31 * result + (shouldThrow != null ? shouldThrow.hashCode() : 0)
            return result
        }
    }
}
