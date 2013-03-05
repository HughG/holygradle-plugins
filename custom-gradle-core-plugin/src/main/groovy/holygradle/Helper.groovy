package holygradle.customgradle

class Helper {
    public static String makeCamelCase(def components) {
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
    
    public static String makeCamelCase(String... components) {
        def componentList = []
        components.each { componentList.add(it) }
        makeCamelCase(componentList)
    }
}