package holygradle.test

import static org.junit.Assert.assertTrue

class AbstractHolyGradleTest {
    protected RegressionFileHelper regression

    AbstractHolyGradleTest() {
        regression = new RegressionFileHelper(this)
    }

    /**
     * Calls a closure with a given {@link Closeable} object, and closes that object in a finally block (unless it is
     * {@code null}.
     * @param closeable The {@link Closeable} object to close once the {@code closure} exits.
     * @param closure The {@link Closure} to call with the {@code closeable} as its single argument.
     */
    public static void useCloseable(Closeable closeable, Closure closure) {
        try {
            closure(closeable)
        } finally {
            if (closeable != null) {
                closeable.close()
            }
        }
    }

    protected File getTestDir() {
        return new File("src/test/groovy/" + getClass().getName().replace(".", "/"))
    }

    protected void ensureFileDeleted(File file) {
        if (file.exists()) {
            assertTrue("Removed existing ${file}", file.delete())
        }
    }
    protected void ensureDirDeleted(File dir) {
        if (dir.exists()) {
            assertTrue("Removed existing ${dir}", dir.deleteDir())
        }
    }
}
