package org.plafue.cucumber.confluence.formatter;

import java.util.HashMap;
import java.util.Map;

import gherkin.formatter.Format;
import org.plafue.cucumber.confluence.exceptions.FormatNotFoundException;

public class Macros extends ConfluenceStorageFormat {

    public static enum Formats {
        INFO, PANEL, JIRA, EXPANDABLE
    }

    private final Map<Formats, Format> formats;

    public Macros() {
        this.formats = new HashMap<Formats, Format>() {{
            put(Formats.PANEL, new Macro("panel"));
            put(Formats.INFO, new Macro("info"));
            put(Formats.EXPANDABLE, new StructuredMacro("expand"));
        }};
    }

    public Macros(final String server) {
        this();
        if (server == null) {
            throw new IllegalStateException("A server is needed for the Jira Issue Macro to work");
        }
        formats.put(Formats.JIRA, new JiraIssueMacro(server));
    }

    public static class Macro implements Format {
        private String macroName;

        public Macro(String macroName) {
            this.macroName = macroName;
        }

        public String text(String text) {
            return new EnclosingFormat("ac:macro", "ac:name=\"" + macroName + "\"")
                    .text(new EnclosingFormat("ac:rich-text-body")
                            .text(text));
        }
    }

    public static class StructuredMacro implements Format {
        private String macroName;

        public StructuredMacro(String macroName) {
            this.macroName = macroName;
        }

        @Override
        public String text(String text) {
            return new EnclosingFormat("ac:structured-macro", "ac:name=\"" + macroName + "\"")
                    .text(new EnclosingFormat("ac:parameter", "ac:name=\"title\"").text("Expand...") + new EnclosingFormat("ac:rich-text-body")
                            .text(text));
        }

        public String titledText(String title, String text) {

            return new EnclosingFormat("ac:structured-macro", "ac:name=\"" + macroName + "\"")
                    .text(new EnclosingFormat("ac:parameter", "ac:name=\"title\"").text(title) + "\n" +
                            new EnclosingFormat("ac:rich-text-body")
                                    .text(text));
        }
    }

    public static class JiraIssueMacro implements Format {
        private final String server;

        public JiraIssueMacro(String server) {
            this.server = server;
        }

        public String text(String jiraId) {
            return new EnclosingFormat("ac:macro", "ac:name=\"jira\"").text(
                    new EnclosingFormat("ac:parameter", "ac:name=\"server\"").text(server) +
                            new EnclosingFormat("ac:parameter", "ac:name=\"key\"").text(jiraId));
        }
    }

    public Format get(String key) {
        Format format = formats.get(Formats.valueOf(key));
        if (format == null) throw new FormatNotFoundException(key);
        return format;
    }

    public Format get(Formats key) {
        return formats.get(key);
    }

    public String up(int n) {
        return "";
    }
}


