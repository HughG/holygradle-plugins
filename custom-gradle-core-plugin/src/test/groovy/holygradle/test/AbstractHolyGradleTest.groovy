package holygradle.test

class AbstractHolyGradleTest {
    protected RegressionFileHelper regression

    AbstractHolyGradleTest() {
        regression = new RegressionFileHelper(this)
    }
    
    protected File getTestDir() {
        return new File("src/test/groovy/" + getClass().getName().replace(".", "/"))
    }
}
