package org.unicode.cldr.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.tool.ShowLanguages.FormattedFileWriter;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.DtdType;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.CoreCoverageInfo;
import org.unicode.cldr.util.CoreCoverageInfo.CoreItems;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PluralSnapshot;
import org.unicode.cldr.util.SimpleFactory;
import org.unicode.cldr.util.PathHeader.Factory;
import org.unicode.cldr.util.RegexLookup;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.VettingViewer;
import org.unicode.cldr.util.VettingViewer.MissingStatus;

import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.ULocale;

public class ShowLocaleCoverage {
    private static final double CORE_SIZE 
    = (double)(CoreItems.values().length - CoreItems.ONLY_RECOMMENDED.size());
    public static TestInfo testInfo = TestInfo.getInstance();
    private static final SupplementalDataInfo SUPPLEMENTAL_DATA_INFO = testInfo.getSupplementalDataInfo();
    private static final CLDRFile ENGLISH = testInfo.getEnglish();
    private static final StandardCodes STANDARD_CODES = testInfo.getStandardCodes();
    // added info using pattern in VettingViewer.

    final static Options myOptions = new Options();
    private static final String OUT_DIRECTORY = CLDRPaths.GEN_DIRECTORY + "/coverage/"; // CldrUtility.MAIN_DIRECTORY;

    enum MyOptions {
        filter(".+", ".*", "Filter the information based on id, using a regex argument."),
//        draftStatus(".+", "unconfirmed", "Filter the information to a minimum draft status."),
        organization(".+", null, "Only locales for organization"),
        version(".+", "25.0", "To get different versions");

        // targetDirectory(".+", CldrUtility.CHART_DIRECTORY + "keyboards/", "The target directory."),
        // layouts(null, null, "Only create html files for keyboard layouts"),
        // repertoire(null, null, "Only create html files for repertoire"), ;
        // boilerplate
        final Option option;

        MyOptions(String argumentPattern, String defaultArgument, String helpText) {
            option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
        }
    }

    static final EnumSet<Level> skipPrintingLevels = EnumSet.of(
        Level.UNDETERMINED,
        Level.CORE,
        Level.POSIX,
        Level.MINIMAL,
        Level.OPTIONAL,
        Level.COMPREHENSIVE
        );

    private static final boolean USE_SEED = false;

    static RegexLookup<Boolean> SKIP_PATHS = new RegexLookup<Boolean>()
        .add("\\[@alt=\"accounting\"]", true)
        .add("\\[@alt=\"variant\"]", true)
        .add("^//ldml/localeDisplayNames/territories/territory.*@alt=\"short", true)
        .add("^//ldml/localeDisplayNames/languages/language.*_", true)
        .add("^//ldml/numbers/currencies/currency.*/symbol", true)
        .add("^//ldml/characters/exemplarCharacters", true);

    static org.unicode.cldr.util.Factory factory = testInfo.getCldrFactory();
    static DraftStatus minimumDraftStatus = DraftStatus.unconfirmed;
    static final Factory pathHeaderFactory = PathHeader.getFactory(ENGLISH);
    
    public static void main(String[] args) throws IOException {
        myOptions.parse(MyOptions.filter, args, true);
        Matcher matcher = Pattern.compile(MyOptions.filter.option.getValue()).matcher("");
        Set<String> locales = null;
        String organization = MyOptions.organization.option.getValue();
        boolean useOrgLevel = MyOptions.organization.option.doesOccur();
        if (useOrgLevel) {
            locales = STANDARD_CODES.getLocaleCoverageLocales(organization);
        }

        if (MyOptions.version.option.doesOccur()) {
            String number = MyOptions.version.option.getValue().trim();
            if (!number.contains(".")) {
                number += ".0";
            }
            factory = org.unicode.cldr.util.Factory.make(
                CLDRPaths.ARCHIVE_DIRECTORY + "cldr-" + number + "/common/main/", ".*");
        } else {
            if (USE_SEED) {
                CLDRConfig cldrConfig = CLDRConfig.getInstance();
                factory = SimpleFactory.make(cldrConfig.getCLDRDataDirectories("common/main,seed/main"), ".*");
            }
        }

        //showEnglish();

        showCoverage(null, matcher, locales, useOrgLevel);
    }

    public static void showCoverage(PrintWriter index) throws IOException {
        showCoverage(index, Pattern.compile(".*").matcher(""), null, false);
    }


    public static void showCoverage(PrintWriter index, Matcher matcher, Set<String> locales, boolean useOrgLevel) throws IOException {
        final String title = "Locale Coverage";
        final PrintWriter pw = new PrintWriter(new FormattedFileWriter(index, title, null, index == null));
        printData(pw, locales, matcher, useOrgLevel);
        ShowPlurals.appendBlanksForScrolling(pw);
        pw.close();
    }

    public static void showEnglish() {
        Map<PathHeader,String> sorted = new TreeMap<>();
        for (String path : ENGLISH) {
            Level currentLevel = SUPPLEMENTAL_DATA_INFO.getCoverageLevel(path, "en");
            if (currentLevel.compareTo(Level.MINIMAL) <= 0) {
                PathHeader ph = pathHeaderFactory.fromPath(path);
                sorted.put(ph, currentLevel + "\t" + ENGLISH.getStringValue(path));
            }
        }
        for (Entry<PathHeader, String> entry : sorted.entrySet()) {
            System.out.println(entry.getKey() + "\t" + entry.getValue());
        }
    }

    static void printData(PrintWriter pw, Set<String> locales, Matcher matcher, boolean useOrgLevel) {
        Set<String> checkModernLocales = STANDARD_CODES.getLocaleCoverageLocales("google", EnumSet.of(Level.MODERN));
        Set<String> availableLanguages = new TreeSet(factory.getAvailableLanguages());
        availableLanguages.addAll(checkModernLocales);

        System.out.println("# Checking: " + availableLanguages);
        pw.println("<p style='text-align: left'>This chart shows the coverage levels for this release. " +
            "The UC% figures include unconfirmed values: these values are typically ignored by implementations. " +
            "A high-level summary of the meaning of the coverage values are at " +
            "<a target='_blank' href='http://www.unicode.org/reports/tr35/tr35-info.html#Coverage_Levels'>Coverage Levels</a>. " +
            "The Core values are described on " +
            "<a target='_blank' href='http://cldr.unicode.org/index/cldr-spec/minimaldata'>Core Data</a>." +
            "</p>");


        Relation<MissingStatus, String> missingPaths = Relation.of(new EnumMap<MissingStatus, Set<String>>(
            MissingStatus.class), TreeSet.class, CLDRFile.getComparator(DtdType.ldml));
        Set<String> unconfirmed = new TreeSet<String>(CLDRFile.getComparator(DtdType.ldml));

        LanguageTagParser ltp = new LanguageTagParser();
        //Map<String, String> likely = testInfo.getSupplementalDataInfo().getLikelySubtags();
        Set<String> defaultContents = SUPPLEMENTAL_DATA_INFO.getDefaultContentLocales();
        CLDRFile english = ENGLISH;

        // Map<String,Counter<Level>> counts = new HashMap();
        //        System.out.print("Script\tEnglish\tNative\tCode\tCode*");
        //        for (Level level : Level.values()) {
        //            if (skipPrintingLevels.contains(level)) {
        //                continue;
        //            }
        //            System.out.print("\t≤" + level + " (f)\t(u)\t(m)");
        //        }
        //        System.out.println();
        // Factory pathHeaderFactory = PathHeader.getFactory(testInfo.getCldrFactory().make("en", true));

        PrintWriter out;
        try {
            out = BagFormatter.openUTF8Writer(OUT_DIRECTORY, "simpleCoverage.tsv");
        } catch (IOException e1) {
            throw new IllegalArgumentException(e1);
        }

        out.println("#Script\tEnglish Name\tNative Name\tCode\tRank\tLevel" +
            "\tEnglish Value\tNative Value\tCorrect Value (or ‘OK’ if F is good)" +
            "\tStatus\tSection\tPage\tHeader\tCode\tPath");

        Counter<Level> foundCounter = new Counter<Level>();
        Counter<Level> unconfirmedCounter = new Counter<Level>();
        Counter<Level> missingCounter = new Counter<Level>();

        List<Level> reversedLevels = Arrays.asList(Level.values());
        java.util.Collections.reverse(reversedLevels);

        int localeCount = 0;

        final TablePrinter tablePrinter = new TablePrinter()
        .addColumn("Code", "class='source'", CldrUtility.getDoubleLinkMsg(), "class='source'", true).setBreakSpans(true)
        .addColumn("English Name", "class='source'", null, "class='source'", true).setBreakSpans(true)
        .addColumn("Native Name", "class='source'", null, "class='source'", true).setBreakSpans(true)
        .addColumn("Script", "class='source'", null, "class='source'", true).setBreakSpans(true)
        //.addColumn("Target Level", "class='target'", null, "class='target'", true).setBreakSpans(true)
        ;

        for (Level level : reversedLevels) {
            if (skipPrintingLevels.contains(level)) {
                continue;
            }
            String titleLevel = level.toString();
            tablePrinter
            .addColumn(UCharacter.toTitleCase(titleLevel,null) + "%", "class='target'", null, "class='targetRight'", true)
            .setCellPattern("{0,number,0%}")
            .setBreakSpans(true);
            if (level == Level.MODERN) {
                tablePrinter.setSortPriority(0).setSortAscending(false);
            }
            tablePrinter
            .addColumn(UCharacter.toTitleCase(titleLevel,null) + " UC%", "class='target'", null, "class='targetRight'", true)
            .setCellPattern("{0,number,0%}")
            .setBreakSpans(true);
        }
        tablePrinter
        .addColumn("Core", "class='target'", null, "class='targetRight'", true)
        .setCellPattern("{0,number,0%}")
        .setBreakSpans(true);

        long start = System.currentTimeMillis();
        LikelySubtags likelySubtags = new LikelySubtags();

        EnumMap<Level,Double> targetLevel = new EnumMap<>(Level.class);
        targetLevel.put(Level.BASIC, 16/100d);
        targetLevel.put(Level.MODERATE, 33/100d);
        targetLevel.put(Level.MODERN, 100/100d);

//        NumberFormat percentFormat = NumberFormat.getPercentInstance(ULocale.ENGLISH);
//        percentFormat.setMaximumFractionDigits(2);
//        percentFormat.setMinimumFractionDigits(2);
//        NumberFormat intFormat = NumberFormat.getIntegerInstance(ULocale.ENGLISH);

        for (String locale : availableLanguages) {
            try {
                if (locale.contains("supplemental")) { // for old versions
                    continue;
                }
                if (locales != null && !locales.contains(locale)) {
                    String base = CLDRLocale.getInstance(locale).getLanguage();
                    if (!locales.contains(base)) {
                        continue;
                    }
                }
                if (!matcher.reset(locale).matches()) {
                    continue;
                }
                if (defaultContents.contains(locale) || "root".equals(locale)) {
                    continue;
                }
                //boolean capture = locale.equals("en");
                String region = ltp.set(locale).getRegion();
                if (!region.isEmpty()) continue; // skip regions

                String max = likelySubtags.maximize(locale);
                String script = ltp.set(max).getScript();

                String language = likelySubtags.minimize(locale);
                // use these as a proxy for 'cldr'
                Level currentLevel = STANDARD_CODES.getLocaleCoverageLevel("google", locale);
                Level otherLevel = STANDARD_CODES.getLocaleCoverageLevel("apple", locale);
                if (otherLevel.compareTo(currentLevel) > 0 
                    && otherLevel.compareTo(Level.MODERN) <= 0) {
                    currentLevel = otherLevel;
                }

                final CLDRFile file = factory.make(locale, true, minimumDraftStatus);

                missingPaths.clear();
                unconfirmed.clear();

                VettingViewer.getStatus(ENGLISH.fullIterable(), file,
                    pathHeaderFactory, foundCounter, unconfirmedCounter,
                    missingCounter, missingPaths, unconfirmed);

                tablePrinter
                .addRow()
                .addCell(language)
                .addCell(ENGLISH.getName(language))
                .addCell(file.getName(language))
                .addCell(script)
                //.addCell(currentLevel)
                ;
                String header = 
                    language
                    + "\t" + ENGLISH.getName(language)
                    + "\t" + file.getName(language)
                    + "\t" + script
                    + "\t" + currentLevel
                    ;

                int sumFound = 0;
                int sumMissing = 0;
                int sumUnconfirmed = 0;
                double modernUnconfirmedCoverage = 0.0d;
                double modernConfirmedCoverage = 0.0d;
                StringBuilder b = new StringBuilder();

                // get the totals

                EnumMap<Level,Integer> totals = new EnumMap<>(Level.class);
                EnumMap<Level,Integer> confirmed = new EnumMap<>(Level.class);
                EnumMap<Level,Integer> unconfirmedByLevel = new EnumMap<>(Level.class);
                for (Level level : Level.values()) {
                    sumFound += foundCounter.get(level);
                    sumUnconfirmed += unconfirmedCounter.get(level);
                    sumMissing += missingCounter.get(level);
                    
                    confirmed.put(level, sumFound);
                    unconfirmedByLevel.put(level, sumFound + sumUnconfirmed);
                    totals.put(level, sumFound + sumUnconfirmed + sumMissing);
                }
                double modernTotal = totals.get(Level.MODERN);
                double modernConfirmed = confirmed.get(Level.MODERN);


                // print the totals

                for (Level level : reversedLevels) {
                    if (useOrgLevel && currentLevel != level) {
                        continue;
                    } else if (skipPrintingLevels.contains(level)) {
                        continue;
                    }
                    int confirmedCoverage = confirmed.get(level);
                    int unconfirmedCoverage = unconfirmedByLevel.get(level);
                    double total = totals.get(level);
                    
                    tablePrinter
                    .addCell(confirmedCoverage / total)
                    .addCell(unconfirmedCoverage / total);
                    
                    Double factor = targetLevel.get(level) / (total / modernTotal);
                    header += "\t" + factor * confirmedCoverage / modernTotal
                        + "\t" + factor * unconfirmedCoverage / modernTotal
                        ;
                }
                Set<String> detailedErrors = new LinkedHashSet<>();
                Set<CoreItems> coverage = new TreeSet<>(
                    CoreCoverageInfo.getCoreCoverageInfo(file, detailedErrors));
                coverage.removeAll(CoreItems.ONLY_RECOMMENDED);
                Set<CoreItems> missing = EnumSet.allOf(CoreItems.class);
                missing.removeAll(coverage);
                missing.removeAll(CoreItems.ONLY_RECOMMENDED);

                double coreValue = coverage.size() / CORE_SIZE;
                tablePrinter
                .addCell(coreValue)
                .finishRow();

                System.out.println(header + "\t" + 0.01 * coreValue + "\t" + CollectionUtilities.join(missing, ", "));

                // Write missing paths (for >99% and specials

                if ((modernConfirmed/modernTotal) >= 0.99d
                    || checkModernLocales.contains(locale)) {
                    for (String path : unconfirmed) {
                        PathHeader ph = pathHeaderFactory.fromPath(path);
                        String line = header + "\t" + english.getStringValue(path)
                            + "\t" + file.getStringValue(path)
                            + "\t" + "UNCONFIRMED"
                            + "\t" + ph + "\t" + path;
                        if (SKIP_PATHS.get(path) != null) {
                            //System.out.println("\nSKIP: " + line);
                        } else {
                            out.println(line);
                        }
                    }
                    for (Entry<MissingStatus, String> entry : missingPaths.entrySet()) {
                        String path = entry.getValue();
                        PathHeader ph = pathHeaderFactory.fromPath(path);
                        String line = header + "\t" + english.getStringValue(path)
                            + "\t???"
                            + "\t" + entry.getKey()
                            + "\t" + ph + "\t" + path;
                        if (SKIP_PATHS.get(path) != null) {
                            //System.out.println("\nSKIP: " + line);
                        } else {
                            out.println(line);
                        }
                    }
                    out.flush();
                }

                localeCount++;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        pw.println(tablePrinter.toTable());
        out.close();

        long end = System.currentTimeMillis();
        System.out.println((end - start) + " millis = "
            + ((end - start) / localeCount) + " millis/locale");

        //        CoverageLevel2 coverageLevel2 = CoverageLevel2.getInstance("en");
        //
        //        for (Entry<MissingStatus, Set<String>> entity : missingPaths.keyValuesSet()) {
        //            for (PathHeader s : CldrUtility.transform(entity.getValue(), pathHeaderFactory, new TreeSet<PathHeader>())) {
        //                System.out.println(entity.getKey() + "\t" + coverageLevel2.getLevel(s.getOriginalPath()) + "\t" + s
        //                        + "\t\t" + s.getOriginalPath());
        //            }
        //        }
    }
}
