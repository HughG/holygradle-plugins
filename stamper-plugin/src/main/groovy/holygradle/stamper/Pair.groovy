package holygradle.stamper

import java.util.regex.Pattern

class Pair {
    public Pattern m_first
    public String m_second
    Pair (Pattern first, String second) {
        m_first = first
        m_second = second
    }
}