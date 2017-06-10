package holygradle.logging

/**
 * Copyright (c) 2016 Hugh Greene (githugh@tameter.org).
 */
interface StyledTextOutput {

    void println(String str)

    void println()

    void withStyle(StyledTextOutput.Style style, Closure action)

    static enum Style {
        Normal('     '),
        Header('hdr  '),
        UserInput('input'),
        Identifier('id   '),
        Description('dsc  '),
        ProgressStatus('...  '),
        Success('OK   '),
        SuccessHeader('OKh  '),
        Failure('FAIL '),
        FailureHeader('FAILh'),
        Info('Inf  '),
        Error('ERR  ');

        private final String name

        Style(String name) {
            this.name = name
        }

        @Override
        public String toString() {
            return name
        }
    }
}