package holygradle.process

import org.gradle.api.Project
import org.gradle.process.internal.ExecException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class ExecHelperTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none()

    @Test
    public void testThrowForExitValueWhenMatched() {
        thrown.expect(ExecException.class)
        doTestThrowForExitValue(23, 23)
    }

    @Test
    public void testThrowForExitValueWhenUnmatched() {
        doTestThrowForExitValue(23, 1)
    }

    @Test
    public void testThrowForExitValueWhenZeroExit() {
        doTestThrowForExitValue(0, 1)
    }

    @Test
    public void testThrowWhenExecThrows() {
        thrown.expect(ExecException.class)
        final Project project = ProjectBuilder.builder().build()
        ExecHelper.executeAndReturnResultAsString(
            project.logger,
            project.&exec,
            { it.commandLine "non_existent_executable" },
            { false }
        )
    }

    private void doTestThrowForExitValue(int actual, int expected) {
        final Project project = ProjectBuilder.builder().build()
        ExecHelper.executeAndReturnResultAsString(
            project.logger,
            project.&exec,
            { it.commandLine "cmd", "/c", "exit /b ${actual}" },
            { expected == it }
        )
    }
}
