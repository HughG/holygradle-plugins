package holygradle.util

public class Wildcard {
    public static boolean match(String pattern, String input) {
        for (chunk in pattern.split("\\*")) {
            int index = input.indexOf(chunk)
            if (index < 0) {
                return false
            }
            input = input.substring(index + chunk.length())
        }
        
        return true
    }
    
    public static boolean anyMatch(def patterns, String input) {
        for (pattern in patterns) {
            if (wildcardMatch(pattern, input)) {
                return true
            }
        }
        return false
    }
}