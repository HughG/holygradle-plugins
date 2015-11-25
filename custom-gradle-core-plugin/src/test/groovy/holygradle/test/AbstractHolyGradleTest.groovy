package holygradle.test

class AbstractHolyGradleTest {
    protected final RegressionFileHelper regression

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
}
