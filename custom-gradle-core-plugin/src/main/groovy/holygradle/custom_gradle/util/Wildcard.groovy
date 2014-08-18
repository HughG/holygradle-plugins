package holygradle.custom_gradle.util

import java.util.regex.Matcher
import java.lang.StringBuilder

public class Wildcard {
    public static boolean match(String pattern, String input) {

        // Convert to a regex
        StringBuilder regexPattern = new StringBuilder();

        pattern.toList().each { String c ->
            String append;
            if (c == '.') {
                append = "\\."
            } else if (c == '?') {
                append = ".?"
            } else if (c == '*') {
                append = ".*"
            } else {
                append = c;
            }
            regexPattern.append(append);
        }

        String regexPatternString = regexPattern.toString()
        Matcher globRegex = input =~ regexPatternString
        return globRegex.matches();

    }
    
    public static boolean anyMatch(List<String> patterns, String input) {
        return patterns.any { match(it, input) }
    }
}