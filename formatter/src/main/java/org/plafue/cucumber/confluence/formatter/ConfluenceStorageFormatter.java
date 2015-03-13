package org.plafue.cucumber.confluence.formatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import gherkin.formatter.Format;
import gherkin.formatter.Formatter;
import gherkin.formatter.NiceAppendable;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Comment;
import gherkin.formatter.model.DataTableRow;
import gherkin.formatter.model.DescribedStatement;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Row;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;
import gherkin.formatter.model.TagStatement;
import gherkin.util.Mapper;

import static gherkin.util.FixJava.join;
import static gherkin.util.FixJava.map;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;
import static org.plafue.cucumber.confluence.formatter.ConfluenceStorageFormat.Formats.*;
import static org.plafue.cucumber.confluence.formatter.Macros.Formats.*;

/**
 * This class pretty prints feature files in Confluence Markup (tested with v4.1.22).
 * This class prints "Feature", "Background", and "scenarios" with their tags if any.
 */
public class ConfluenceStorageFormatter implements Formatter {


    private static final String NEWLINE = "\\r\\n|\\r|\\n";
    public static final String JIRA_ISSUE_ID_FORMAT = "@[A-Z][A-Z]+-[0-9]{1,9}";
    private final NiceAppendable out;
    private final Options options;
    private final Macros macros;
    private final ConfluenceStorageFormat formats;
    private StringBuilder sb = new StringBuilder();

    private List<Step> steps = new ArrayList<>();
    private DescribedStatement statement;
    private Mapper<Tag, String> tagNameMapper = tag ->
            getFormat(BOLD).text(getFormat(ITALICS).text(tag.getName().replace("@", "")));
    private String sectionName;
    private StringBuilder sectionTitle;

    public ConfluenceStorageFormatter(Appendable out, Options options) {
        this.out = new NiceAppendable(out);
        this.options = options;
        this.formats = new ConfluenceStorageFormat();
        if (options.isJiraTicketParsingInTags()) {
            this.macros = new Macros(options.getJiraServer());
        } else {
            this.macros = new Macros();
        }
    }

    @Override
    public void uri(String uri) {
    }

    @Override
    public void feature(Feature feature) {
        this.sb.append(getFormat(HEADER1).text(feature.getName()));
        printTags(this.sb, feature.getTags());
        String description = feature.getDescription().replaceAll(NEWLINE, " ");
        if (!description.isEmpty()) {
            this.sb.append(description);
        }
    }

    @Override
    public void background(Background background) {
        replay(this.sb, Optional.of(background));
        statement = background;
    }

    @Override
    public void scenario(Scenario scenario) {
        replay(this.sb, Optional.of(scenario));
        statement = scenario;
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        replay(this.sb, Optional.of(scenarioOutline));
        statement = scenarioOutline;
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        // NoOp
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        // NoOp
    }

    @Override
    public void examples(Examples examples) {
        replay(this.sb, Optional.empty());
        this.sb.append("\n");
        printComments(this.sb, examples.getComments(), " ");
        printTags(this.sb, examples.getTags());
        this.sb.append(getFormat(TABLE).text(
                getFormat(TABLE_ROW).text(getFormat(TABLE_HEAD_CELL).text(examples.getKeyword() + ": " + examples.getName())) +
                        getFormat(TABLE_ROW).text(getFormat(CELL).text(getMacro(PANEL).text(renderTable(examples.getRows()))))));
    }

    @Override
    public void step(Step step) {
        steps.add(step);
    }

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void done() {
    }

    @Override
    public void close() {
        out.close();
    }

    public void eof() {
        replay(this.sb, Optional.empty());
        out.println(this.sb.toString());
        this.sb = new StringBuilder();
    }

    private void replay(StringBuilder sb, Optional<DescribedStatement> statement) {

        StringBuilder title = new StringBuilder();
        printSectionTitle(title);
        title.append("\n");
        statement.ifPresent(s -> {
            this.sectionName = statement.get().getName();
            this.sectionTitle = title;
        });
        StringBuilder steps = new StringBuilder();
        printSteps(steps);

        statement.ifPresent(s -> System.out.println(s.getName()));

        if (!statement.isPresent()) {
            Macros.StructuredMacro macro = (Macros.StructuredMacro) getMacro(EXPANDABLE);
            String text = macro.titledText(sectionName, steps.toString());
            System.out.println("<<<<");
            System.out.println(sectionName);
            System.out.println("====");
            System.out.println(steps.toString());
            System.out.println(">>>>");
            sb.append(text);
            sectionName = null;
        }
    }

    private void printSteps(StringBuilder sb) {
        if (steps.isEmpty()) return;


        final StringBuilder tableContents = new StringBuilder();
        while (!steps.isEmpty()) {
            printStep(tableContents);
        }
        sb.append(getFormat(TABLE).text(tableContents.toString()));
    }

    private void printSectionTitle(StringBuilder sb) {
        if (statement == null) return;

        sb.append(
                formats.get(HEADER2).text(
                        statement.getName().isEmpty() ?
                                getFormat(RED_FOREGROUND).text(getFormat(ITALICS).text("Undefined section")) :
                                statement.getName()
                ));

        if (statement instanceof TagStatement) {
            printTags(sb, ((TagStatement) statement).getTags());
        }
        sb.append(statement.getDescription());
        statement = null;
    }

    private void printStep(StringBuilder stringBuilder) {
        Step step = steps.remove(0);
        Format keyword = getFormat(CELL_ALIGNED_RIGHT);
        Format cell = getFormat(CELL);
        Format row = getFormat(TABLE_ROW);
        Format bold = getFormat(BOLD);
        stringBuilder.append(
                row.text(
                        keyword.text(getFormat(COLOR_DARK_GREY).text(bold.text(step.getKeyword().trim()))) +
                                cell.text(escapeHtml4(step.getName().trim()))));

        if (hasNestedTable(step)) {
            stringBuilder.append(renderNestedTableWithinPanelInSecondColumn(step.getRows()));
        }
    }

    private String renderNestedTableWithinPanelInSecondColumn(List<DataTableRow> rows) {
        return getFormat(TABLE_ROW).text(
                getFormat(CELL).text("") +
                        getFormat(CELL).text(getMacro(PANEL).text(renderTable(rows))));
    }

    private boolean hasNestedTable(Step step) {
        return step.getRows() != null;
    }

    private Format getFormat(ConfluenceStorageFormat.Formats key) {
        return formats.get(key);
    }

    private Format getMacro(Macros.Formats key) {
        return macros.get(key);
    }

    private String renderTable(List<? extends Row> rows) {
        if (rows.isEmpty()) return "";

        Format table = getFormat(TABLE);
        StringBuilder tableContents = new StringBuilder();

        for (int i = 0; i < rows.size(); i++) {
            Format cellFormat = isHeaderRow(i) ? getFormat(TABLE_HEAD_CELL) : getFormat(CELL);
            tableContents.append(
                    getFormat(TABLE_ROW).text(renderCells(rows.get(i), cellFormat).toString())
            );
        }

        return table.text(tableContents.toString());
    }

    private StringBuilder renderCells(Row row, Format cellFormat) {
        StringBuilder cells = new StringBuilder();
        for (String cellContents : row.getCells()) {
            cells.append(cellFormat.text(escapeHtml4(cellContents)));
        }
        return cells;
    }

    private boolean isHeaderRow(int i) {
        return (i == 0);
    }

    private void printComments(StringBuilder sb, List<Comment> comments, String indent) {
        for (Comment comment : comments) {
            sb.append(indent)
                    .append(comment.getValue());
        }
    }

    private void printTags(StringBuilder sb, List<Tag> tags) {
        if (tags.isEmpty() || !options.isTagRenderingActive() ||
                (options.isJiraTicketParsingInTags() && options.jiraServer == null)) return;

        List<Tag> jiraIds = Collections.emptyList();

        if (options.isJiraTicketParsingInTags()) {
            jiraIds = findJiraIdsAndExtractFromOriginalList(tags);
        }

        if (!tags.isEmpty()) {
            sb.append(getMacro(INFO).text(
                    " This section is tagged as " +
                            join(map(tags, tagNameMapper), ", ")));
        }

        if (!jiraIds.isEmpty()) {
            printJiraMacros(sb, jiraIds);
        }
    }

    private void printJiraMacros(StringBuilder sb, List<Tag> jiraIds) {
        sb.append(join(map(jiraIds, new Mapper<Tag, String>() {
            @Override
            public String map(Tag tag) {
                return getMacro(JIRA).text(tag.getName().replace("@", ""));
            }
        }), System.lineSeparator()));
    }

    private List<Tag> findJiraIdsAndExtractFromOriginalList(List<Tag> tags) {
        return tags.stream()
                .filter(tag -> !tag.getName().matches(JIRA_ISSUE_ID_FORMAT))
                .collect(Collectors.toList());
    }

    public static class Options {
        private boolean tagRenderingActive;
        private boolean jiraTicketParsingInTags;
        private String jiraServer;

        public Options(boolean tagRenderingActive) {
            this.tagRenderingActive = tagRenderingActive;
        }

        public Options(String jiraServer) {
            if (jiraServer == null) {
                throw new IllegalStateException("A Jira server must be provided");
            }
            this.tagRenderingActive = true;
            this.jiraTicketParsingInTags = true;
            this.jiraServer = jiraServer;
        }

        public boolean isTagRenderingActive() {
            return tagRenderingActive;
        }

        public String getJiraServer() {
            return jiraServer;
        }

        public void setJiraServer(String jiraServer) {
            this.jiraServer = jiraServer;
        }

        public boolean isJiraTicketParsingInTags() {
            return jiraTicketParsingInTags;
        }
    }
}