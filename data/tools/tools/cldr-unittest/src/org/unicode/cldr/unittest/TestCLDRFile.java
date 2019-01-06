package org.unicode.cldr.unittest;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.DtdType;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PatternCache;
import org.unicode.cldr.util.SimpleFactory;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.impl.Relation;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.util.Output;

public class TestCLDRFile extends TestFmwk {
    private static final boolean DISABLE_TIL_WORKS = false;

    static TestInfo testInfo = TestInfo.getInstance();
    static SupplementalDataInfo sdi = testInfo.getSupplementalDataInfo();

    public static void main(String[] args) {
        new TestCLDRFile().run(args);
    }

    public void testFallbackNames() {
        String[][] tests = {
            {"zh-Hanb", "Chinese (Han with Bopomofo)"},
            {"aaa", "Ghotuo"},
            {"zh-RR", "Chinese (RR)"},
            {"new_Newa_NP", "Newari (Newa, Nepal)"},
        };
        CLDRFile english = testInfo.getEnglish(); // testInfo.getFullCldrFactory().make("en", false);
        for (String[] test : tests) {
            assertEquals("", test[1], english.getName(test[0]));
        }
    }


    // verify for all paths, if there is a count="other", then there is a
    // count="x", for all x in keywords
    public void testPlurals() {
        for (String locale : new String[] { "fr", "en", "root", "ar", "ja" }) {
            checkPlurals(locale);
        }
    }

    static final Pattern COUNT_MATCHER = Pattern
        .compile("\\[@count=\"([^\"]+)\"]");

    private void checkPlurals(String locale) {
        CLDRFile cldrFile = testInfo.getCLDRFile(locale, true);
        Matcher m = COUNT_MATCHER.matcher("");
        Relation<String, String> skeletonToKeywords = Relation.of(
            new TreeMap<String, Set<String>>(cldrFile.getComparator()),
            TreeSet.class);
        PluralInfo plurals = sdi.getPlurals(PluralType.cardinal, locale);
        Set<String> normalKeywords = plurals.getCanonicalKeywords();
        for (String path : cldrFile.fullIterable()) {
            if (!path.contains("@count")) {
                continue;
            }
            if (!m.reset(path).find()) {
                throw new IllegalArgumentException();
            }
            String skeleton = path.substring(0, m.start(1)) + ".*"
                + path.substring(m.end(1));
            skeletonToKeywords.put(skeleton, m.group(1));
        }
        for (Entry<String, Set<String>> entry : skeletonToKeywords
            .keyValuesSet()) {
            assertEquals(
                "Incorrect keywords: " + locale + ", " + entry.getKey(),
                normalKeywords, entry.getValue());
        }
    }

    static Factory cldrFactory = testInfo.getCldrFactory();

    static class LocaleInfo {
        final String locale;
        final CLDRFile cldrFile;
        final Set<String> paths = new HashSet<String>();

        LocaleInfo(String locale) {
            this.locale = locale;
            cldrFile = testInfo.getCLDRFile(locale, true);
            for (String path : cldrFile.fullIterable()) {
                Level level = sdi.getCoverageLevel(path, locale);
                if (level.compareTo(Level.COMPREHENSIVE) > 0) {
                    continue;
                }
                if (path.contains("[@count=")
                    && !path.contains("[@count=\"other\"]")) {
                    continue;
                }
                paths.add(path);
            }
        }
    }

    public void testExtraPaths() {
        Map<String, LocaleInfo> localeInfos = new LinkedHashMap<String, LocaleInfo>();
        Relation<String, String> missingPathsToLocales = Relation.of(
            new TreeMap<String, Set<String>>(CLDRFile
                .getComparator(DtdType.ldml)), TreeSet.class);
        Relation<String, String> extraPathsToLocales = Relation.of(
            new TreeMap<String, Set<String>>(CLDRFile
                .getComparator(DtdType.ldml)), TreeSet.class);

        for (String locale : new String[] { "en", "root", "fr", "ar", "ja" }) {
            localeInfos.put(locale, new LocaleInfo(locale));
        }
        LocaleInfo englishInfo = localeInfos.get("en");
        for (String path : englishInfo.paths) {
            if (path.startsWith("//ldml/identity/")
                || path.startsWith("//ldml/numbers/currencies/currency[@type=")
                // || path.startsWith("//ldml/dates/calendars/calendar") &&
                // !path.startsWith("//ldml/dates/calendars/calendar[@type=\"gregorian\"]")
                // ||
                // path.startsWith("//ldml/numbers/currencyFormats[@numberSystem=")
                // &&
                // !path.startsWith("//ldml/numbers/currencyFormats[@numberSystem=\"latn\"]")
                || path.contains("[@count=")
                && !path.contains("[@count=\"other\"]")
                || path.contains("dayPeriod[@type=\"noon\"]")) {
                continue;
            }
            for (LocaleInfo localeInfo : localeInfos.values()) {
                if (localeInfo == englishInfo) {
                    continue;
                }
                if (!localeInfo.paths.contains(path)) {
                    if (path.startsWith("//ldml/dates/calendars/calendar")
                        && !(path.contains("[@type=\"generic\"]") || path
                            .contains("[@type=\"gregorian\"]"))
                            || (path.contains("/eras/") && path
                                .contains("[@alt=\"variant\"]")) // it is OK
                                // for
                                // just
                                // "en"
                                // to
                                // have
                                // /eras/.../era[@type=...][@alt="variant"]
                                || path.contains("[@type=\"japanese\"]")
                                || path.contains("[@type=\"coptic\"]")
                                || path.contains("[@type=\"hebrew\"]")
                                || path.contains("[@type=\"islamic-rgsa\"]")
                                || path.contains("[@type=\"islamic-umalqura\"]")
                                || path.contains("/relative[@type=\"-2\"]")
                                || path.contains("/relative[@type=\"2\"]")
                                || path.startsWith("//ldml/contextTransforms/contextTransformUsage")
                                || path.contains("[@alt=\"variant\"]")
                                || (path.contains("dayPeriod[@type=")
                        && (path.endsWith("1\"]") || path.endsWith("\"am\"]") || path.endsWith("\"pm\"]") || path.endsWith("\"midnight\"]")
                        )) // morning1, afternoon1, ...
                                        || (path.startsWith("//ldml/characters/exemplarCharacters[@type=\"index\"]")
                                            && localeInfo.locale.equals("root"))
                                            // //ldml/characters/exemplarCharacters[@type="index"][root]
                        ) {
                        continue;
                    }
                    String localeAndStatus = localeInfo.locale
                        + (englishInfo.cldrFile.isHere(path) ? "" : "*");
                    missingPathsToLocales.put(path, localeAndStatus);
                    // English contains the path, and the target locale doesn't.
                    // The * means that the value is inherited (eg from root).
                }
            }
        }

        for (LocaleInfo localeInfo : localeInfos.values()) {
            if (localeInfo == englishInfo) {
                continue;
            }
            for (String path : localeInfo.paths) {
                if (path.contains("[@numberSystem=\"arab\"]")
                    || path.contains("[@type=\"japanese\"]")
                    || path.contains("[@type=\"coptic\"]")
                    || path.contains("[@type=\"hebrew\"]")
                    || path.contains("[@type=\"islamic-rgsa\"]")
                    || path.contains("[@type=\"islamic-umalqura\"]")
                    || path.contains("/relative[@type=\"-2\"]")
                    || path.contains("/relative[@type=\"2\"]")) {
                    continue;
                }
                if (!englishInfo.paths.contains(path)) {
                    String localeAndStatus = localeInfo.locale
                        + (localeInfo.cldrFile.isHere(path) ? "" : "*");
                    extraPathsToLocales.put(path, localeAndStatus);
                    // English doesn't contains the path, and the target locale does.
                    // The * means that the value is inherited (eg from root).
                }
            }
        }

        for (Entry<String, Set<String>> entry : missingPathsToLocales
            .keyValuesSet()) {
            String path = entry.getKey();
            Set<String> locales = entry.getValue();
            Status status = new Status();
            String originalLocale = englishInfo.cldrFile.getSourceLocaleID(
                path, status);
            String engName = "en"
                + (englishInfo.cldrFile.isHere(path) ? "" : " (source_locale:"
                    + originalLocale
                    + (path.equals(status.pathWhereFound) ? "" : ", source_path: "
                        + status) + ")");
            if (path.startsWith("//ldml/localeDisplayNames/")
                || path.contains("[@alt=\"accounting\"]")) {
                logln("+" + engName + ", -" + locales + "\t" + path);
            } else {
                errln("+" + engName + ", -" + locales + "\t" + path);
            }
        }
        for (Entry<String, Set<String>> entry : extraPathsToLocales
            .keyValuesSet()) {
            String path = entry.getKey();
            Set<String> locales = entry.getValue();
            if (path.startsWith("//ldml/localeDisplayNames/")
                || path.startsWith("//ldml/numbers/otherNumberingSystems/")
                // || path.contains("[@alt=\"accounting\"]")
                ) {
                logln("-en, +" + locales + "\t" + path);
            } else {
                logln("-en, +" + locales + "\t" + path);
            }
        }

        // for (String locale : new String[] { "fr", "ar", "ja" }) {
        // CLDRFile cldrFile = cldrFactory.make(locale, true);
        // Set<String> s = (Set<String>) cldrFile.getExtraPaths(new
        // TreeSet<String>());
        // System.out.println("Extras for " + locale);
        // for (String path : s) {
        // System.out.println(path + " => " + cldrFile.getStringValue(path));
        // }
        // System.out.println("Already in " + locale);
        // for (Iterator<String> it =
        // cldrFile.iterator(PatternCache.get(".*\\[@count=.*").matcher(""));
        // it.hasNext();) {
        // String path = it.next();
        // System.out.println(path + " => " + cldrFile.getStringValue(path));
        // }
        // }
    }

    // public void testDraftFilter() {
    // Factory cldrFactory = Factory.make(CldrUtility.MAIN_DIRECTORY, ".*",
    // DraftStatus.approved);
    // checkLocale(cldrFactory.make("root", true));
    // checkLocale(cldrFactory.make("ee", true));
    // }

    public void checkLocale(CLDRFile cldr) {
        Matcher m = PatternCache.get("gregorian.*eras").matcher("");
        for (Iterator<String> it = cldr.iterator("",
            new UTF16.StringComparator()); it.hasNext();) {
            String path = it.next();
            if (m.reset(path).find() && !path.contains("alias")) {
                errln(cldr.getLocaleID() + "\t" + cldr.getStringValue(path)
                    + "\t" + cldr.getFullXPath(path));
            }
            if (path == null) {
                errln("Null path");
            }
            String fullPath = cldr.getFullXPath(path);
            if (fullPath.contains("@draft")) {
                errln("File can't contain draft elements");
            }
        }
    }

    // public void testTimeZonePath() {
    // Factory cldrFactory = Factory.make(CldrUtility.MAIN_DIRECTORY, ".*");
    // String tz = "Pacific/Midway";
    // CLDRFile cldrFile = cldrFactory.make("lv", true);
    // String retVal = cldrFile.getStringValue(
    // "//ldml/dates/timeZoneNames/zone[@type=\"" + tz + "\"]/exemplarCity"
    // , true).trim();
    // errln(retVal);
    // }

    public void testSimple() {
        double deltaTime = System.currentTimeMillis();
        CLDRFile english = testInfo.getEnglish();
        deltaTime = System.currentTimeMillis() - deltaTime;
        logln("Creation: Elapsed: " + deltaTime / 1000.0 + " seconds");

        deltaTime = System.currentTimeMillis();
        english.getStringValue("//ldml");
        deltaTime = System.currentTimeMillis() - deltaTime;
        logln("Creation: Elapsed: " + deltaTime / 1000.0 + " seconds");

        deltaTime = System.currentTimeMillis();
        english.getStringValue("//ldml");
        deltaTime = System.currentTimeMillis() - deltaTime;
        logln("Caching: Elapsed: " + deltaTime / 1000.0 + " seconds");

        deltaTime = System.currentTimeMillis();
        for (int j = 0; j < 2; ++j) {
            for (Iterator<String> it = english.iterator(); it.hasNext();) {
                String dpath = it.next();
                String value = english.getStringValue(dpath);
                Set<String> paths = english.getPathsWithValue(value, "", null,
                    null);
                if (paths.size() == 0) {
                    continue;
                }
                if (!paths.contains(dpath)) {
                    if (DISABLE_TIL_WORKS) {
                        errln("Missing " + dpath + " in "
                            + pathsWithValues(value, paths));
                    }
                }
                if (paths.size() > 1) {
                    Set<String> nonAliased = getNonAliased(paths, english);
                    if (nonAliased.size() > 1) {
                        logln(pathsWithValues(value, nonAliased));
                    }
                }
            }
        }
        deltaTime = System.currentTimeMillis() - deltaTime;
        logln("Elapsed: " + deltaTime / 1000.0 + " seconds");
    }

    private String pathsWithValues(String value, Set<String> paths) {
        return paths.size() + " paths with: <" + value + ">\t\tPaths: "
            + paths.iterator().next() + ",...";
    }

    private Set<String> getNonAliased(Set<String> paths, CLDRFile file) {
        Set<String> result = new LinkedHashSet<String>();
        for (String path : paths) {
            if (file.isHere(path)) {
                result.add(path);
            }
        }
        return result;
    }

    public void testResolution() {
        CLDRFile german = testInfo.getCLDRFile("de", true);
        // Test direct lookup.
        String xpath = "//ldml/localeDisplayNames/localeDisplayPattern/localeSeparator";
        String id = german.getSourceLocaleID(xpath, null);
        if (!id.equals("de")) {
            errln("Expected de but was " + id + " for " + xpath);
        }

        // Test aliasing.
        xpath = "//ldml/dates/calendars/calendar[@type=\"islamic-civil\"]/dateTimeFormats/availableFormats/dateFormatItem[@id=\"yyyyMEd\"]";
        id = german.getSourceLocaleID(xpath, null);
        if (!id.equals("de")) {
            errln("Expected de but was " + id + " for " + xpath);
        }

        // Test lookup that falls to root.
        xpath = "//ldml/dates/calendars/calendar[@type=\"coptic\"]/months/monthContext[@type=\"stand-alone\"]/monthWidth[@type=\"narrow\"]/month[@type=\"5\"]";
        id = german.getSourceLocaleID(xpath, null);
        if (!id.equals("root")) {
            errln("Expected root but was " + id + " for " + xpath);
        }
    }

    static final NumberFormat percent = NumberFormat.getPercentInstance();

    static final class Size {
        int items;
        int chars;

        public void add(String topValue) {
            items++;
            chars += topValue.length();
        }

        public String over(Size base) {
            return "items: " + items + "("
                + percent.format(items / (0.0 + base.items)) + "); "
                + "chars: " + chars + "("
                + percent.format(chars / (0.0 + base.chars)) + ")";
        }
    }

    public void testGeorgeBailey() {
        PathHeader.Factory phf = PathHeader.getFactory(testInfo.getEnglish());
        for (String locale : Arrays.asList("de", "de_AT", "en", "nl")) {
            CLDRFile cldrFile = testInfo.getCLDRFile(locale, true);

            CLDRFile cldrFileUnresolved = testInfo.getCLDRFile(locale, false);
            Status status = new Status();
            Output<String> localeWhereFound = new Output<String>();
            Output<String> pathWhereFound = new Output<String>();

            Map<String, String> diff = new TreeMap<String, String>(
                CLDRFile.getComparator(DtdType.ldml));

            Size countSuperfluous = new Size();
            Size countExtraLevel = new Size();
            Size countOrdinary = new Size();

            for (String path : cldrFile.fullIterable()) {
                String baileyValue = cldrFile.getBaileyValue(path,
                    pathWhereFound, localeWhereFound);
                String topValue = cldrFileUnresolved.getStringValue(path);
                String resolvedValue = cldrFile.getStringValue(path);

                // if there is a value, then either it is at the top level or it
                // is the bailey value.

                if (resolvedValue != null) {
                    if (topValue != null) {
                        assertEquals(
                            "top≠resolved\t" + locale + "\t"
                                + phf.fromPath(path), topValue,
                                resolvedValue);
                    } else {
                        String locale2 = cldrFile.getSourceLocaleID(path,
                            status);
                        assertEquals(
                            "bailey value≠\t" + locale + "\t"
                                + phf.fromPath(path), resolvedValue,
                                baileyValue);
                        assertEquals(
                            "bailey locale≠\t" + locale + "\t"
                                + phf.fromPath(path), locale2,
                                localeWhereFound.value);
                        assertEquals(
                            "bailey path≠\t" + locale + "\t"
                                + phf.fromPath(path),
                                status.pathWhereFound, pathWhereFound.value);
                    }
                }

                if (topValue != null) {
                    if (CldrUtility.equals(topValue, baileyValue)) {
                        countSuperfluous.add(topValue);
                    } else if (sdi.getCoverageLevel(path, locale).compareTo(
                        Level.MODERN) > 0) {
                        countExtraLevel.add(topValue);
                    }
                    countOrdinary.add(topValue);

                    // String parentValue = parentFile.getStringValue(path);
                    // if (!CldrUtility.equals(parentValue, baileyValue)) {
                    // diff.put(path, "parent=" + parentValue + ";\tbailey=" +
                    // baileyValue);
                    // }
                }
            }
            logln("Superfluous (" + locale + "):\t"
                + countSuperfluous.over(countOrdinary));
            logln(">Modern (" + locale + "):\t"
                + countExtraLevel.over(countOrdinary));
            for (Entry<String, String> entry : diff.entrySet()) {
                logln(locale + "\t" + phf.fromPath(entry.getKey()) + ";\t"
                    + entry.getValue());
            }
        }
    }

    public void TestConstructedBailey() {
        CLDRFile eng = TestInfo.getInstance().getEnglish();

        String prefix = "//ldml/localeDisplayNames/languages/language[@type=\"";
        String display = eng.getConstructedBaileyValue(prefix + "zh_Hans"
            + "\"]", null, null);
        assertEquals("contructed bailey", "Chinese (Simplified)", display);
        display = eng.getConstructedBaileyValue(prefix + "es_US" + "\"]", null,
            null);
        assertEquals("contructed bailey", "Spanish (United States)", display);
        display = eng.getConstructedBaileyValue(prefix + "es_US"
            + "\"][@alt=\"short\"]", null, null);
        assertEquals("contructed bailey", "Spanish (US)", display);
        display = eng.getConstructedBaileyValue(prefix + "es" + "\"]", null,
            null);
        assertEquals("contructed bailey", "es", display);
        display = eng.getConstructedBaileyValue(prefix + "missing" + "\"]",
            null, null);
        assertEquals("contructed bailey", null, display);
    }

    public void TestFileLocations() {
        File mainDir = new File(CLDRPaths.MAIN_DIRECTORY);
        if (!mainDir.isDirectory()) {
            throw new IllegalArgumentException(
                "MAIN_DIRECTORY is not a directory: "
                    + CLDRPaths.MAIN_DIRECTORY);
        }
        File mainCollationDir = new File(CLDRPaths.COLLATION_DIRECTORY);
        if (!mainCollationDir.isDirectory()) {
            throw new IllegalArgumentException(
                "COLLATION_DIRECTORY is not a directory: "
                    + CLDRPaths.COLLATION_DIRECTORY);
        }
        File seedDir = new File(CLDRPaths.SEED_DIRECTORY);
        if (!seedDir.isDirectory()) {
            throw new IllegalArgumentException(
                "SEED_DIRECTORY is not a directory: "
                    + CLDRPaths.SEED_DIRECTORY);
        }
        File seedCollationDir = new File(CLDRPaths.SEED_COLLATION_DIRECTORY);
        if (!seedCollationDir.isDirectory()) {
            throw new IllegalArgumentException(
                "SEED_COLLATION_DIRECTORY is not a directory: "
                    + CLDRPaths.SEED_COLLATION_DIRECTORY);
        }
        File[] md = { mainDir, mainCollationDir };
        File[] sd = { seedDir, seedCollationDir };
        Factory mf = SimpleFactory.make(md, ".*", DraftStatus.unconfirmed);
        Factory sf = SimpleFactory.make(sd, ".*", DraftStatus.unconfirmed);
        Set<CLDRLocale> mainLocales = mf.getAvailableCLDRLocales();
        Set<CLDRLocale> seedLocales = sf.getAvailableCLDRLocales();
        mainLocales.retainAll(seedLocales);
        if (!mainLocales.isEmpty()) {
            errln("CLDR locale files located in both common and seed ==> "
                + mainLocales.toString());
        }
    }

    public void TestForStrayFiles() {
        TreeSet<String> mainList = new TreeSet<>(Arrays.asList(new File(CLDRPaths.MAIN_DIRECTORY).list()));

        for (String dir : CLDRPaths.LDML_DIRECTORIES) {
            Set<String> dirFiles = new TreeSet<String>(Arrays.asList(new File(CLDRPaths.BASE_DIRECTORY + "common/" + dir).list()));
            if (dir.equals("rbnf")) { // Remove known exceptions.
                dirFiles.remove("es_003.xml");
            }
            if (!mainList.containsAll(dirFiles)) {
                dirFiles.removeAll(mainList);
                errln(dir + " has extra files" + dirFiles);
            }
        }
    }

}
