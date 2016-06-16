
// Todo: Check that the last character of all matches is a "/"

// Todo: It would be nice if we could re-load the file without having the re-load the server

// Todo: Make a TreeMap to make searching faster

download {
    // NOTE 2016-01-20 HughG: I want to read the set of groups from a file, but the plugin API gives me no way to
    // get the plugin directory or something like that.  From experimentation, the current directory when the
    // "executions" block is executed is the "bin" folder of the installation.
    final File ARTIFACTORY_ROOT = new File("..")
    final NavigableMap<String, String> BLACKLIST_MODULES = new File(ARTIFACTORY_ROOT, "etc/plugins/moduleBlacklist.txt").readLines().inject(new TreeMap<String, String>()) { map, token ->
        token.split('=').with { map[it[0].trim()] = it[1].trim() }
        map
    }

    altResponse { request, responseRepoPath ->
        String path = responseRepoPath.getPath();
        // Todo: This should work, it doesn't
        /*Map.Entry<String, String> test = BLACKLIST_MODULES.ceilingEntry(path);
        if (test != null && path.startsWith(test.key)) {
            status = 451;
            message = test.value;
            return;
        }*/

        BLACKLIST_MODULES.each { key, value ->
            if (responseRepoPath.getPath().startsWith(key)) {
                status = 410; // Return a 410: GONE status
                message = value;
            }
        }
    }
}