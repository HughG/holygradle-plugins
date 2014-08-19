package holygradle.credentials;

/**
 * Simple holder class for username and password.
 */
public final class Credentials {
    public final String userName;
    public final String password;

    public Credentials(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Credentials that = (Credentials) o;

        if (password != null ? !password.equals(that.password) : that.password != null) return false;
        //noinspection RedundantIfStatement
        if (userName != null ? !userName.equals(that.userName) : that.userName != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = userName != null ? userName.hashCode() : 0;
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }
}
