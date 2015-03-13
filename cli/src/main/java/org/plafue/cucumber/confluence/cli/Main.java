package org.plafue.cucumber.confluence.cli;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.ParseException;
import org.plafue.cucumber.confluence.filesystem.FeatureFinder;
import org.plafue.cucumber.confluence.formatter.ConfluenceStorageFormatter;
import org.plafue.cucumber.confluence.parser.BatchParser;

public class Main {

    public static void main(String[] args) throws IOException, ParseException {
        CliOptions options = new CliOptions(args);
        FeatureFinder finder = new FeatureFinder(options.fileToParse());
        BatchParser parser = new BatchParser();
        List<File> features = finder.findFeatures();
        ConfluenceStorageFormatter.Options formatterOptions = new ConfluenceStorageFormatter.Options(options.renderTags());
        parser.parseIntoSingleFile(features, formatterOptions, options.outputDir());
    }
}
