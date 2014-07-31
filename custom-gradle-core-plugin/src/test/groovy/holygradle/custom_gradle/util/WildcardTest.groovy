package holygradle.custom_gradle.util;

import holygradle.custom_gradle.util.Wildcard

import org.junit.Test
import static org.junit.Assert.*

public class WildcardTest {

    @Test
    void AsteriskMatch() {
        assertTrue(Wildcard.match("*", 'should'));
        assertTrue(Wildcard.match("*", 'match'));
        assertTrue(Wildcard.match("*", '*ANYTHING*'));
        assertTrue(Wildcard.match("*", ''));
        assertTrue(Wildcard.match("**", '*ANYTHING*'));
    }

    @Test
    void PrefixMatch() {
        assertFalse(Wildcard.match("bar*","foobar"));
        assertFalse(Wildcard.match("bar*",""));

        assertTrue(Wildcard.match("bar*", "barfood"));
        assertTrue(Wildcard.match("bar*","bar"));
        assertTrue(Wildcard.match("bar*","barbar"));
    }

    @Test
    void PostfixMatch() {
        assertFalse(Wildcard.match("*foo", "foobar"));
        assertFalse(Wildcard.match("*foo", ""));

        assertTrue(Wildcard.match("*bar", "foobar"));
        assertTrue(Wildcard.match("*foo", "foo"));
        assertTrue(Wildcard.match("*foo", "foofoo"));
    }

    @Test
    void QuestionmarkMatch() {
        assertFalse(Wildcard.match("?", "foobar"));
        assertFalse(Wildcard.match("?b", "foobar"));
        assertFalse(Wildcard.match("b?", "foobar"));
        assertFalse(Wildcard.match("r?", "foobar"));

        assertTrue(Wildcard.match("foob?r", "foobar"));
        assertTrue(Wildcard.match("foob?r", "foobar"));
        assertTrue(Wildcard.match("*b?r", "foobar"));
        assertTrue(Wildcard.match("f?o*", "foobar"));
    }

    @Test
    void StringWithDotsMatch() {
        assertFalse(Wildcard.match(".*", "hello"));
        assertFalse(Wildcard.match("*.*", "hello"));

        assertTrue(Wildcard.match("no.way", "no.way"));
        assertTrue(Wildcard.match("no.*way", "no.Anyway"));
        assertTrue(Wildcard.match("no.*way", "no..Anyway"));

        assertTrue(Wildcard.match(".*", ".hello"));
        assertTrue(Wildcard.match("*.*", "hello.world"));
    }

    @Test
    void PlainStringMatch() {
        assertFalse(Wildcard.match("bar", "foobar"));
        assertFalse(Wildcard.match("foo", "foobar"));
        assertFalse(Wildcard.match("FooBar", "foobar"));
        assertFalse(Wildcard.match("", "foobar"));

        assertTrue(Wildcard.match("foobar", "foobar"));
    }
}