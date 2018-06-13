package holygradle.test

import org.junit.Assert

class AssertHelper {
    public static void assertThrows(String message, Class clazz, Closure block) {
        try {
            block{}
            Assert.fail("${message}: Expected exception of type ${clazz} but got none")
        } catch (Exception e) {
            if (!clazz.isAssignableFrom(e.class)) {
                Assert.fail("${message}: Expected exception of type ${clazz} but got ${e}")
            }
        }
    }
}
