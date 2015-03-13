package org.plafue.cucumber.confluence.parser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import gherkin.util.FixJava;
import org.plafue.cucumber.confluence.formatter.ConfluenceStorageFormatter;

public class BatchParser {

    public void parse(List<File> features, ConfluenceStorageFormatter.Options formatterOptions, File outputDir) throws IOException {

        for (File feature : features) {
            File outputFile = new File(outputDir, feature.getName().replace(".feature", ".xhtml"));
            ConfluenceStorageFormatter confluenceStorageFormatter = new ConfluenceStorageFormatter(new FileWriter(outputFile), formatterOptions);
            new gherkin.parser.Parser(confluenceStorageFormatter).parse(FixJava.readReader(new FileReader(feature)), "", 0);
        }
    }

    public void parseIntoSingleFile(List<File> features, ConfluenceStorageFormatter.Options formatterOptions, File outputDir) throws IOException {
        File outputFile = new File(outputDir, "cucumber-report.xhtml");
        FileWriter file = new FileWriter(outputFile);
        ConfluenceStorageFormatter confluenceStorageFormatter = new ConfluenceStorageFormatter(file, formatterOptions);

        gherkin.parser.Parser parser = new gherkin.parser.Parser(confluenceStorageFormatter);
        for (File feature : features) {
            String s = FixJava.readReader(new FileReader(feature));
            parser.parse(s, "", 0);
        }
    }
}
