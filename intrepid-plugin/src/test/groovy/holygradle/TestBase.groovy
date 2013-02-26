package holygradle

import org.junit.Test
import java.io.File
import static org.junit.Assert.*

class TestBase {
    protected RegressionFileHelper regression
    
    TestBase() {
        regression = new RegressionFileHelper(this)
    }
    
    protected String getTestDir() {
        return "src/test/groovy/" + getClass().getName().replace(".", "/")
    }
}