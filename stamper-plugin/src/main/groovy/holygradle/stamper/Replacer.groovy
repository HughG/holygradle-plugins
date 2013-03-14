package holygradle.stamper

import java.util.regex.Pattern

class Replacer {
    String m_filePattern
    File m_file
    def regexFindAndReplace = []
    
    Replacer(String filePattern) {
        m_filePattern = filePattern
    }
    Replacer(File file) {
        m_file = file
    }
    
    void replaceRegex (Pattern regex, String replacementString) {
        regexFindAndReplace.add(new Pair(regex, replacementString))
    }

    void doPatternReplacement() {
        doStringReplacementInFile(m_file)
    }
    
    void doPatternReplacement(File file) {        
        if (file.name.endsWith(m_filePattern)) {
            doStringReplacementInFile(file)
        }
    }
    
    void doStringReplacementInFile(File file) {
        def origRcText = file.text
        def rcText = origRcText
        for (replacement in regexFindAndReplace) {
            if(replacement.m_second) {                    
                rcText = rcText.replaceAll(replacement.m_first as Pattern) { 
                    all, start, end -> "${start}${replacement.m_second}${end}"
                }
            }
        }
        if (origRcText != rcText) {
            file.write(rcText)
        }    
    }
}