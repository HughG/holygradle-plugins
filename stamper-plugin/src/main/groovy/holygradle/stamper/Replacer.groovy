package holygradle.stamper

import java.util.regex.Pattern

class Replacer {
    private class Pair {
        public Pattern pattern
        public String replacement
        Pair (Pattern first, String second) {
            pattern = first
            replacement = second
        }
    }

    private String filePattern
    private File file
    private Collection<Pair> regexFindAndReplace = []
    
    Replacer(String filePattern) {
        this.filePattern = filePattern
    }

    Replacer(File file) {
        this.file = file
    }
    
    void replaceRegex(Pattern regex, String replacementString) {
        regexFindAndReplace.add(new Pair(regex, replacementString))
    }

    void doPatternReplacement() {
        doStringReplacementInFile(file)
    }
    
    void doPatternReplacement(File file) {
        if (file.name.endsWith(filePattern)) {
            doStringReplacementInFile(file)
        }
    }
    
    void doStringReplacementInFile(File file) {
        String origRcText = file.text
        String rcText = origRcText
        for (replacement in regexFindAndReplace) {
            if (replacement.replacement) {
                rcText = rcText.replaceAll(replacement.pattern) {
                    all, start, end -> "${start}${replacement.replacement}${end}"
                }
            }
        }
        if (origRcText != rcText) {
            file.write(rcText)
        }    
    }
}