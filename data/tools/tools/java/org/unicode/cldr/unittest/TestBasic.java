package org.unicode.cldr.unittest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.DisplayAndInputProcessor;
import org.unicode.cldr.tool.LikelySubtags;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.Builder;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.CLDRFile.WinningChoice;
import org.unicode.cldr.util.CharacterFallbacks;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XPathParts;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.ULocale;

public class TestBasic extends TestFmwk {
    static TestInfo testInfo = TestInfo.getInstance();

    /**
     * Simple test that loads each file in the cldr directory, thus verifying that
     * the DTD works, and also checks that the PrettyPaths work.
     * 
     * @author markdavis
     */

    public static void main(String[] args) {
        new TestBasic().run(args);
    }

    private static final Set<String> skipAttributes = new HashSet<String>(Arrays.asList("alt", "draft",
        "references"));

    private final String localeRegex = CldrUtility.getProperty("locale", ".*");

    private final String commonDirectory = CldrUtility.COMMON_DIRECTORY;

    private final String mainDirectory = CldrUtility.MAIN_DIRECTORY;

    // private final boolean showForceZoom = Utility.getProperty("forcezoom", false);

    private final boolean resolved = CldrUtility.getProperty("resolved", false);

    private final Exception[] internalException = new Exception[1];

    public void TestDtds() throws IOException {
        checkDtds(commonDirectory + "/collation");
        checkDtds(commonDirectory + "/main");
        checkDtds(commonDirectory + "/rbnf");
        checkDtds(commonDirectory + "/segments");
        checkDtds(commonDirectory + "/supplemental");
        checkDtds(commonDirectory + "/transforms");
    }

    private void checkDtds(String directory) throws IOException {
        File directoryFile = new File(directory);
        File[] listFiles = directoryFile.listFiles();
        String canonicalPath = directoryFile.getCanonicalPath();
        if (listFiles == null) {
            throw new IllegalArgumentException("Empty directory: " + canonicalPath);
        }
        logln("Checking files for DTD errors in: " + canonicalPath);
        for (File fileName : listFiles) {
            if (!fileName.toString().endsWith(".xml") || fileName.getName().startsWith(".")) {
                continue;
            }
            check(fileName);
        }
    }

    class MyErrorHandler implements ErrorHandler {
        public void error(SAXParseException exception) throws SAXException {
            errln("error: " + XMLFileReader.showSAX(exception));
            throw exception;
        }

        public void fatalError(SAXParseException exception) throws SAXException {
            errln("fatalError: " + XMLFileReader.showSAX(exception));
            throw exception;
        }

        public void warning(SAXParseException exception) throws SAXException {
            errln("warning: " + XMLFileReader.showSAX(exception));
            throw exception;
        }
    }

    public void check(File systemID) {
        try {
            FileInputStream fis = new FileInputStream(systemID);
            XMLReader xmlReader = XMLFileReader.createXMLReader(true);
            xmlReader.setErrorHandler(new MyErrorHandler());
            InputSource is = new InputSource(fis);
            is.setSystemId(systemID.toString());
            xmlReader.parse(is);
            fis.close();
        } catch (SAXParseException e) {
            errln("\t" + "Can't read " + systemID + "\t" + e.getClass() + "\t" + e.getMessage());
        } catch (SAXException e) {
            errln("\t" + "Can't read " + systemID + "\t" + e.getClass() + "\t" + e.getMessage());
        } catch (IOException e) {
            errln("\t" + "Can't read " + systemID + "\t" + e.getClass() + "\t" + e.getMessage());
        }
    }

    public void TestCurrencyFallback() {
        XPathParts parts = new XPathParts();
        Factory cldrFactory = Factory.make(mainDirectory, localeRegex);
        Set<String> currencies = StandardCodes.make().getAvailableCodes("currency");

        final UnicodeSet CHARACTERS_THAT_SHOULD_HAVE_FALLBACKS = (UnicodeSet) new UnicodeSet(
            "[[:sc:]-[\\u0000-\\u00FF]]").freeze();

        CharacterFallbacks fallbacks = CharacterFallbacks.make();

        for (String locale : cldrFactory.getAvailable()) {
            CLDRFile file = cldrFactory.make(locale, false);
            if (file.isNonInheriting())
                continue;

            final UnicodeSet OK_CURRENCY_FALLBACK = (UnicodeSet) new UnicodeSet("[\\u0000-\\u00FF]")
                .addAll(safeExemplars(file, ""))
                .addAll(safeExemplars(file, "auxiliary"))
                .addAll(safeExemplars(file, "currencySymbol"))
                .freeze();
            UnicodeSet badSoFar = new UnicodeSet();

            for (Iterator<String> it = file.iterator(); it.hasNext();) {
                String path = it.next();
                if (path.endsWith("/alias")) {
                    continue;
                }
                String value = file.getStringValue(path);

                // check for special characters

                if (CHARACTERS_THAT_SHOULD_HAVE_FALLBACKS.containsSome(value)) {

                    parts.set(path);
                    if (!parts.getElement(-1).equals("symbol")) {
                        continue;
                    }
                    String currencyType = parts.getAttributeValue(-2, "type");

                    UnicodeSet fishy = new UnicodeSet().addAll(value).retainAll(CHARACTERS_THAT_SHOULD_HAVE_FALLBACKS)
                        .removeAll(badSoFar);
                    for (UnicodeSetIterator it2 = new UnicodeSetIterator(fishy); it2.next();) {
                        final int fishyCodepoint = it2.codepoint;
                        List<String> fallbackList = fallbacks.getSubstitutes(fishyCodepoint);

                        String nfkc = Normalizer.normalize(fishyCodepoint, Normalizer.NFKC);
                        if (!nfkc.equals(UTF16.valueOf(fishyCodepoint))) {
                            if (fallbackList == null) {
                                fallbackList = new ArrayList<String>();
                            } else {
                                fallbackList = new ArrayList<String>(fallbackList); // writable
                            }
                            fallbackList.add(nfkc);
                        }
                        // later test for all Latin-1
                        if (fallbackList == null) {
                            errln("Locale:\t" + locale + ";\tCharacter with no fallback:\t" + it2.getString() + "\t"
                                + UCharacter.getName(fishyCodepoint));
                            badSoFar.add(fishyCodepoint);
                        } else {
                            String fallback = null;
                            for (String fb : fallbackList) {
                                if (OK_CURRENCY_FALLBACK.containsAll(fb)) {
                                    if (!fb.equals(currencyType) && currencies.contains(fb)) {
                                        errln("Locale:\t" + locale + ";\tCurrency:\t" + currencyType
                                            + ";\tFallback converts to different code!:\t" + fb
                                            + "\t" + it2.getString() + "\t" + UCharacter.getName(fishyCodepoint));
                                    }
                                    if (fallback == null) {
                                        fallback = fb;
                                    }
                                }
                            }
                            if (fallback == null) {
                                errln("Locale:\t" + locale
                                    + ";\tCharacter with no good fallback (exemplars+Latin1):\t" + it2.getString()
                                    + "\t" + UCharacter.getName(fishyCodepoint));
                                badSoFar.add(fishyCodepoint);
                            } else {
                                logln("Locale:\t" + locale + ";\tCharacter with good fallback:\t"
                                    + it2.getString() + " " + UCharacter.getName(fishyCodepoint)
                                    + " => " + fallback);
                                // badSoFar.add(fishyCodepoint);
                            }
                        }
                    }
                }
            }
        }
    }

    public void TestAbstractPaths() {
        Factory cldrFactory = Factory.make(mainDirectory, localeRegex);
        CLDRFile english = cldrFactory.make("en", true);
        Map<String, Counter<Level>> abstactPaths = new TreeMap<String, Counter<Level>>();
        RegexTransform abstractPathTransform = new RegexTransform(RegexTransform.Processing.ONE_PASS)
            .add("//ldml/", "")
            .add("\\[@alt=\"[^\"]*\"\\]", "")
            .add("=\"[^\"]*\"", "=\"*\"")
            .add("([^]])\\[", "$1\t[")
            .add("([^]])/", "$1\t/")
            .add("/", "\t");

        for (String locale : cldrFactory.getAvailable()) {
            // if (locale.equals("root") && !localeRegex.equals("root"))
            // continue;
            CLDRFile file = cldrFactory.make(locale, resolved);
            if (file.isNonInheriting())
                continue;
            logln(locale + "\t-\t" + english.getName(locale));

            for (Iterator<String> it = file.iterator(); it.hasNext();) {
                String path = it.next();
                if (path.endsWith("/alias")) {
                    continue;
                }
                // collect abstracted paths
                String abstractPath = abstractPathTransform.transform(path);
                Level level = testInfo.getSupplementalDataInfo().getCoverageLevel(path, locale);
                if (level == Level.OPTIONAL) {
                    level = Level.COMPREHENSIVE;
                }
                Counter<Level> row = abstactPaths.get(abstractPath);
                if (row == null) {
                    abstactPaths.put(abstractPath, row = new Counter<Level>());
                }
                row.add(level, 1);
            }
        }
        logln(CldrUtility.LINE_SEPARATOR + "Abstract Paths");
        for (Entry<String, Counter<Level>> pathInfo : abstactPaths.entrySet()) {
            String path = pathInfo.getKey();
            Counter<Level> counter = pathInfo.getValue();
            logln(counter.getTotal() + "\t" + getCoverage(counter) + "\t" + path);
        }
    }

    private CharSequence getCoverage(Counter<Level> counter) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Level level : counter.getKeysetSortedByKey()) {
            if (first) {
                first = false;
            } else {
                result.append(' ');
            }
            result.append("L").append(level.ordinal()).append("=").append(counter.get(level));
        }
        return result;
    }

    public void TestPaths() {
        Relation<String, String> distinguishing = Relation.of(new TreeMap<String, Set<String>>(), TreeSet.class);
        Relation<String, String> nonDistinguishing = Relation.of(new TreeMap<String, Set<String>>(), TreeSet.class);
        XPathParts parts = new XPathParts();
        Factory cldrFactory = testInfo.getCldrFactory();
        CLDRFile english = cldrFactory.make("en", true);

        Relation<String, String> pathToLocale = Relation.of(new TreeMap<String, Set<String>>(CLDRFile.ldmlComparator),
            TreeSet.class, null);

        for (String locale : cldrFactory.getAvailable()) {
            // if (locale.equals("root") && !localeRegex.equals("root"))
            // continue;
            CLDRFile file = cldrFactory.make(locale, resolved);
            if (file.isNonInheriting())
                continue;
            DisplayAndInputProcessor displayAndInputProcessor = new DisplayAndInputProcessor(file, false);

            logln(locale + "\t-\t" + english.getName(locale));

            for (Iterator<String> it = file.iterator(); it.hasNext();) {
                String path = it.next();
                if (path.endsWith("/alias")) {
                    continue;
                }
                String value = file.getStringValue(path);
                if (value == null) {
                    throw new IllegalArgumentException(locale + "\tError: in null value at " + path);
                }

                String displayValue = displayAndInputProcessor.processForDisplay(path, value);
                if (!displayValue.equals(value)) {
                    logln("\t" + locale + "\tdisplayAndInputProcessor changes display value <" + value
                        + ">\t=>\t<" + displayValue + ">\t\t" + path);
                }
                String inputValue = displayAndInputProcessor.processInput(path, value, internalException);
                if (internalException[0] != null) {
                    errln("\t" + locale + "\tdisplayAndInputProcessor internal error <" + value + ">\t=>\t<"
                        + inputValue + ">\t\t" + path);
                    internalException[0].printStackTrace(System.out);
                }
                if (isVerbose() && !inputValue.equals(value)) {
                    displayAndInputProcessor.processInput(path, value, internalException); // for
                    // debugging
                    logln("\t" + locale + "\tdisplayAndInputProcessor changes input value <" + value
                        + ">\t=>\t<" + inputValue + ">\t\t" + path);
                }

                pathToLocale.put(path, locale);

                // also check for non-distinguishing attributes
                if (path.contains("/identity"))
                    continue;

                String fullPath = file.getFullXPath(path);
                parts.set(fullPath);
                for (int i = 0; i < parts.size(); ++i) {
                    if (parts.getAttributeCount(i) == 0)
                        continue;
                    String element = parts.getElement(i);
                    for (String attribute : parts.getAttributeKeys(i)) {
                        if (skipAttributes.contains(attribute))
                            continue;
                        if (CLDRFile.isDistinguishing(element, attribute)) {
                            distinguishing.put(element, attribute);
                        } else {
                            nonDistinguishing.put(element, attribute);
                        }
                    }
                }
            }
        }

        if (isVerbose()) {

            System.out.format("Distinguishing Elements: %s" + CldrUtility.LINE_SEPARATOR, distinguishing);
            System.out.format("Nondistinguishing Elements: %s" + CldrUtility.LINE_SEPARATOR, nonDistinguishing);
            System.out.format("Skipped %s" + CldrUtility.LINE_SEPARATOR, skipAttributes);

            logln(CldrUtility.LINE_SEPARATOR + "Paths to skip in Survey Tool");
            for (String path : pathToLocale.keySet()) {
                if (CheckCLDR.skipShowingInSurvey.matcher(path).matches()) {
                    logln("Skipping: " + path);
                }
            }

            logln(CldrUtility.LINE_SEPARATOR + "Paths to force zoom in Survey Tool");
            for (String path : pathToLocale.keySet()) {
                if (CheckCLDR.FORCE_ZOOMED_EDIT.matcher(path).matches()) {
                    logln("Forced Zoom Edit: " + path);
                }
            }
        }
    }

    /**
     * The verbose output shows the results of 1..3 \u00a4 signs.
     */
    public void checkCurrency() {
        Map<String, Set<R2<String, Integer>>> results = new TreeMap<String, Set<R2<String, Integer>>>(Collator.getInstance(ULocale.ENGLISH));
        for (ULocale locale : ULocale.getAvailableLocales()) {
            if (locale.getCountry().length() != 0) {
                continue;
            }
            for (int i = 1; i < 4; ++i) {
                NumberFormat format = getCurrencyInstance(locale, i);
                for (Currency c : new Currency[] { Currency.getInstance("USD"), Currency.getInstance("EUR"),
                    Currency.getInstance("INR") }) {
                    format.setCurrency(c);
                    final String formatted = format.format(12345.67);
                    Set<R2<String, Integer>> set = results.get(formatted);
                    if (set == null) {
                        results.put(formatted, set = new TreeSet<R2<String, Integer>>());
                    }
                    set.add(Row.of(locale.toString(), Integer.valueOf(i)));
                }
            }
        }
        for (String formatted : results.keySet()) {
            logln(formatted + "\t" + results.get(formatted));
        }
    }

    private static NumberFormat getCurrencyInstance(ULocale locale, int type) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        if (type > 1) {
            DecimalFormat format2 = (DecimalFormat) format;
            String pattern = format2.toPattern();
            String replacement = "\u00a4\u00a4";
            for (int i = 2; i < type; ++i) {
                replacement += "\u00a4";
            }
            pattern = pattern.replace("\u00a4", replacement);
            format2.applyPattern(pattern);
        }
        return format;
    }

    private UnicodeSet safeExemplars(CLDRFile file, String string) {
        final UnicodeSet result = file.getExemplarSet(string, WinningChoice.NORMAL);
        return result != null ? result : new UnicodeSet();
    }

    public void TestAPath() {
        // <month type="1">1</month>
        String path = "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/months/monthContext[@type=\"format\"]/monthWidth[@type=\"abbreviated\"]/month[@type=\"1\"]";
        CLDRFile root = testInfo.getRoot();
        logln("path: " + path);
        String fullpath = root.getFullXPath(path);
        logln("fullpath: " + fullpath);
        String value = root.getStringValue(path);
        logln("value: " + value);
        Status status = new Status();
        String source = root.getSourceLocaleID(path, status);
        logln("locale: " + source);
        logln("status: " + status);
    }

    public void TestDefaultContents() {
        Set<String> defaultContents = testInfo.getSupplementalDataInfo().getDefaultContentLocales();
        Relation<String, String> parentToChildren = Relation.of(new TreeMap(), TreeSet.class);
        for (String child : testInfo.getCldrFactory().getAvailable()) {
            if (child.equals("root")) {
                continue;
            }
            String localeParent = LocaleIDParser.getParent(child);
            parentToChildren.put(localeParent, child);
        }
        for (String locale : defaultContents) {
            CLDRFile cldrFile;
            try {
                cldrFile = testInfo.getCldrFactory().make(locale, false);
            } catch (RuntimeException e) {
                logln("Can't open default content file:\t" + locale);
                continue;
            }
            // we check that the default content locale is always empty
            for (Iterator<String> it = cldrFile.iterator(); it.hasNext();) {
                String path = it.next();
                if (path.contains("/identity")) {
                    continue;
                }
                errln("Default content file not empty:\t" + locale);
                showDifferences(locale);
                break;
            }
        }

        // check that if a locale has any children, that exactly one of them is the default content

        for (String locale : defaultContents) {

            if (locale.equals("en_US")) {
                continue; // en_US_POSIX
            }
            Set<String> children = parentToChildren.get(locale);
            if (children != null) {
                Set<String> defaultContentChildren = new LinkedHashSet(children);
                defaultContentChildren.retainAll(defaultContents);
                if (defaultContentChildren.size() != 1) {
                    if (defaultContentChildren.isEmpty()) {
                        errln("Locale has children but is missing default contents locale: " + locale + ", children: " + children);
                    } else {
                        errln("Locale has too many defaultContent locales!!: " + locale
                            + ", defaultContents: " + defaultContentChildren);
                    }
                }
            }
        }

        // check that each default content locale is likely-subtag equivalent to its parent.

        for (String locale : defaultContents) {
            String maxLocale = LikelySubtags.maximize(locale, likelyData);
            String localeParent = LocaleIDParser.getParent(locale);
            String maxLocaleParent = LikelySubtags.maximize(localeParent, likelyData);
            if (locale.equals("ar_001")) {
                logln("Known exception to likelyMax(locale=" + locale + ")" +
                    " == " +
                    "likelyMax(defaultContent=" + localeParent + ")");
                continue;
            }
            assertEquals(
                "likelyMax(locale=" + locale + ")" +
                    " == " +
                    "likelyMax(defaultContent=" + localeParent + ")",
                maxLocaleParent,
                maxLocale);
        }

    }

    static final Map<String, String> likelyData = testInfo.getSupplementalDataInfo().getLikelySubtags();

    public void TestLikelySubtagsComplete() {
        LanguageTagParser ltp = new LanguageTagParser();
        for (String locale : testInfo.getCldrFactory().getAvailable()) {
            if (locale.equals("root")) {
                continue;
            }
            String maxLocale = LikelySubtags.maximize(locale, likelyData);
            if (maxLocale == null) {
                errln("Locale missing likely subtag: " + locale);
                continue;
            }
            ltp.set(maxLocale);
            if (ltp.getLanguage().isEmpty()
                || ltp.getScript().isEmpty()
                || ltp.getRegion().isEmpty()) {
                errln("Locale has defective likely subtag: " + locale + " => " + maxLocale);
            }
        }
    }

    private void showDifferences(String locale) {
        CLDRFile cldrFile = testInfo.getCldrFactory().make(locale, false);
        final String localeParent = LocaleIDParser.getParent(locale);
        CLDRFile parentFile = testInfo.getCldrFactory().make(localeParent, true);
        int funnyCount = 0;
        for (Iterator<String> it = cldrFile.iterator("", CLDRFile.ldmlComparator); it.hasNext();) {
            String path = it.next();
            if (path.contains("/identity")) {
                continue;
            }
            final String fullXPath = cldrFile.getFullXPath(path);
            if (fullXPath.contains("[@draft=\"unconfirmed\"]") || fullXPath.contains("[@draft=\"provisional\"]")) {
                funnyCount++;
                continue;
            }
            logln("\tpath:\t" + path);
            logln("\t\t" + locale + " value:\t<" + cldrFile.getStringValue(path) + ">");
            final String parentFullPath = parentFile.getFullXPath(path);
            logln("\t\t" + localeParent + " value:\t<" + parentFile.getStringValue(path) + ">");
            logln("\t\t" + locale + " fullpath:\t" + fullXPath);
            logln("\t\t" + localeParent + " fullpath:\t" + parentFullPath);
        }
        logln("\tCount of non-approved:\t" + funnyCount);
    }

    enum MissingType {
        plurals, main_exemplars, no_main, collation, index_exemplars, punct_exemplars
    }

    public void TestCoreData() {
        Set<String> availableLanguages = testInfo.getCldrFactory().getAvailableLanguages();
        PluralInfo rootRules = testInfo.getSupplementalDataInfo().getPlurals(PluralType.cardinal, "root");
        EnumSet<MissingType> errors = EnumSet.of(MissingType.collation);
        EnumSet<MissingType> warnings = EnumSet.of(MissingType.collation, MissingType.index_exemplars,
            MissingType.punct_exemplars);

        Set<String> collations = new HashSet<String>();
        XPathParts parts = new XPathParts();

        // collect collation info
        Factory collationFactory = Factory.make(CldrUtility.COLLATION_DIRECTORY, ".*", DraftStatus.contributed);
        for (String localeID : collationFactory.getAvailable()) {
            if (localeID.equals("root")) {
                CLDRFile cldrFile = collationFactory.make(localeID, false, DraftStatus.contributed);
                for (String path : cldrFile) {
                    if (path.startsWith("//ldml/collations")) {
                        String fullPath = cldrFile.getFullXPath(path);
                        String valid = parts.set(fullPath).getAttributeValue(1, "validSubLocales");
                        for (String validSub : valid.trim().split("\\s+")) {
                            if (isTopLevel(validSub)) {
                                collations.add(validSub);
                            }
                        }
                        break; // done with root
                    }
                }
            } else if (isTopLevel(localeID)) {
                collations.add(localeID);
            }
        }
        logln(collations.toString());

        Set<String> allLanguages = Builder.with(new TreeSet<String>()).addAll(collations).addAll(availableLanguages)
            .freeze();

        for (String localeID : allLanguages) {
            if (localeID.equals("root")) {
                continue; // skip script locales
            }
            if (!isTopLevel(localeID)) {
                continue;
            }

            errors.clear();
            warnings.clear();

            String name = "Locale:" + localeID + " (" + testInfo.getEnglish().getName(localeID) + ")";

            if (!collations.contains(localeID)) {
                warnings.add(MissingType.collation);
                logln(name + " is missing " + MissingType.collation.toString());
            }

            try {
                CLDRFile cldrFile = testInfo.getCldrFactory().make(localeID, false, DraftStatus.contributed);

                String wholeFileAlias = cldrFile.getStringValue("//ldml/alias");
                if (wholeFileAlias != null) {
                    logln("Whole-file alias:" + name);
                    continue;
                }

                PluralInfo pluralInfo = testInfo.getSupplementalDataInfo().getPlurals(PluralType.cardinal, localeID);
                if (pluralInfo == rootRules) {
                    logln(name + " is missing " + MissingType.plurals.toString());
                    warnings.add(MissingType.plurals);
                }
                UnicodeSet main = cldrFile.getExemplarSet("", WinningChoice.WINNING);
                if (main == null || main.isEmpty()) {
                    errln("  " + name + " is missing " + MissingType.main_exemplars.toString());
                    errors.add(MissingType.main_exemplars);
                }
                UnicodeSet index = cldrFile.getExemplarSet("index", WinningChoice.WINNING);
                if (index == null || index.isEmpty()) {
                    logln(name + " is missing " + MissingType.index_exemplars.toString());
                    warnings.add(MissingType.index_exemplars);
                }
                UnicodeSet punctuation = cldrFile.getExemplarSet("punctuation", WinningChoice.WINNING);
                if (punctuation == null || punctuation.isEmpty()) {
                    logln(name + " is missing " + MissingType.punct_exemplars.toString());
                    warnings.add(MissingType.punct_exemplars);
                }
            } catch (Exception e) {
                errln("  " + name + " is missing main locale data.");
                errors.add(MissingType.no_main);
            }

            // report errors

            if (errors.isEmpty() && warnings.isEmpty()) {
                logln(name + ": No problems...");
            }
        }
    }

    private boolean isTopLevel(String localeID) {
        return "root".equals(LocaleIDParser.getParent(localeID));
    }
}
