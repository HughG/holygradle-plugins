package holygradle.custom_gradle.util

public class CamelCase {
    public static String build(List<String> components) {
        def camelCase = new StringBuilder()
        boolean firstCharacterLowerCase = true
        components.each { component ->
            component.split("[_\\-\\s\\/]+").each { chunk ->
                if (chunk != null && chunk.length() > 0) {
                    if (firstCharacterLowerCase) {
                        camelCase.append(chunk[0].toLowerCase())
                    } else {
                        camelCase.append(chunk[0].toUpperCase())
                    }
                    if (chunk.length() > 1) {
                        camelCase.append(chunk[1..-1])
                    }
                    firstCharacterLowerCase = false
                }
            }
        }
        return camelCase
    }
    
    public static String build(String... components) {
        def componentList = []
        components.each { componentList.add(it) }
        build(componentList)
    }
}