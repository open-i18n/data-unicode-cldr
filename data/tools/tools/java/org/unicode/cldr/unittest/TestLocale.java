package org.unicode.cldr.unittest;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.xpath.XPathException;

import org.unicode.cldr.test.CheckDates;
import org.unicode.cldr.test.ExampleGenerator;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.Iso639Data;
import org.unicode.cldr.util.Iso639Data.Scope;
import org.unicode.cldr.util.Iso639Data.Type;
import org.unicode.cldr.util.LanguageTagCanonicalizer;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.SimpleXMLSource;
import org.unicode.cldr.util.StandardCodes.CodeType;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.XPathExpressionParser;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;

public class TestLocale extends TestFmwkPlus {
    static TestInfo testInfo = TestInfo.getInstance();

    public static void main(String[] args) {
        new TestLocale().run(args);
    }

    static Set<Type> ALLOWED_LANGUAGE_TYPES = EnumSet.of(Type.Ancient, Type.Living, Type.Constructed, Type.Historical, Type.Extinct);
    static Set<Scope> ALLOWED_LANGUAGE_SCOPES = EnumSet.of(
        Scope.Individual, Scope.Macrolanguage); // , Special, Collection, PrivateUse, Unknown
    static Set<String> ALLOWED_SCRIPTS = testInfo.getStandardCodes().getGoodAvailableCodes(CodeType.script);
    static Set<String> ALLOWED_REGIONS = testInfo.getStandardCodes().getGoodAvailableCodes(CodeType.territory);

    /**
     * XPath expression that will find all alias tags
     */
    static String XPATH_ALIAS_STRING = "//alias";

    /**
     * Determine whether the file should be checked for aliases; this is currently not done for
     * Keyboard definitions or DTD's
     * @param f the file to check
     * @return
     */
    protected boolean shouldCheckForAliases(File f) {
        if (!f.canRead()) {
            return false;
        }
        String absPath = f.getAbsolutePath();
        return absPath.endsWith("xml") && !absPath.contains("dtd") && !absPath.contains("keyboard") && !absPath.contains("Keyboard");
    }

    /**
     * Check a single file for aliases, on a content level, the only check that is done is that the one for readability. 
     * @param localeName - the localename
     * @param file - the file to check
     * @param localesWithAliases - a set of locale strings the files of which contain aliases
     */
    private void checkForAliases(final String localeName, File file, final Set<String> localesWithAliases) {
        try {
            if (file.canRead()) {
                XPathExpressionParser parser = new XPathExpressionParser(file);
                parser.iterateThroughNodeSet(XPATH_ALIAS_STRING, new XPathExpressionParser.NodeHandlingInterface() {

                    //Handle gets called for every node of the node set
                    @Override
                    public void handle(Node result) {
                        if (result instanceof Element) {
                            Element el = (Element) result;
                            // this node likely has an attribute source
                            if (el.hasAttributes()) {
                                String sourceAttr = el.getAttribute("source");
                                if (sourceAttr != null && !sourceAttr.isEmpty()) {
                                    localesWithAliases.add(localeName);
                                }
                            }
                        }
                    }
                });
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (XPathException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Tests the validity of the file names and of the English localeDisplayName types. 
     * Also tests for aliases outside root
     */
    public void TestLocalePartsValidity() {
        LanguageTagParser ltp = new LanguageTagParser();
        final Set<String> localesWithAliases = new HashSet<>();
        for (File file : CLDRConfig.getInstance().getAllCLDRFilesEndingWith(".xml")) {
            String parent = file.getParent();
            if (parent.contains("transform") || parent.contains("bcp47") || parent.contains("supplemental")) {
                continue;
            }
            String localeName = file.getName();
            localeName = localeName.substring(0, localeName.length() - 4); // remove .xml
            if (localeName.equals("root") || localeName.equals("_platform")) {
                continue;
            }
            String fileString = file.toString();
            checkLocale(fileString, localeName, ltp);
            // check for aliases 
            if (shouldCheckForAliases(file)) {
                checkForAliases(localeName, file, localesWithAliases);
            }
        }
        // we ran through all of them
        if (!localesWithAliases.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n");
            sb.append("The following locales have aliases, but must not: ");
            Iterator<String> lIter = localesWithAliases.iterator();
            while (lIter.hasNext()) {
                sb.append(lIter.next());
                sb.append(" ");
            }
            System.out.println(sb.toString());
        }
        // now check English-resolved
        CLDRFile english = testInfo.getEnglish();
        for (String xpath : english) {
            if (!xpath.startsWith("//ldml/localeDisplayNames/")) {
                continue;
            }
            switch (CLDRFile.getNameType(xpath)) {
            case 0:
                checkLocale("English xpath", CLDRFile.getCode(xpath), ltp);
                break;
            case 1:
                checkScript("English xpath", CLDRFile.getCode(xpath));
                break;
            case 2:
                checkRegion("English xpath", CLDRFile.getCode(xpath));
                break;
            }
        }
    }

    public void checkLocale(String fileString, String localeName, LanguageTagParser ltp) {
        ltp.set(localeName);
        checkLanguage(fileString, ltp.getLanguage());
        checkScript(fileString, ltp.getScript());
        checkRegion(fileString, ltp.getRegion());
    }

    public void checkRegion(String file, String region) {
        if (!region.isEmpty() && !region.equals("AN")) {
            assertRelation("Region ok? " + region + " in " + file, true, ALLOWED_REGIONS, TestFmwkPlus.CONTAINS, region);
        }
    }

    public void checkScript(String file, String script) {
        if (!script.isEmpty()) {
            assertRelation("Script ok? " + script + " in " + file, true, ALLOWED_SCRIPTS, TestFmwkPlus.CONTAINS, script);
        }
    }

    public void checkLanguage(String file, String language) {
        if (!language.equals("und") && !language.equals("root") && !language.equals("zxx") && !language.equals("mul")) {
            Scope scope = Iso639Data.getScope(language);
            if (assertRelation("Language ok? " + language + " in " + file, true, ALLOWED_LANGUAGE_SCOPES, TestFmwkPlus.CONTAINS, scope)) {
                Type type = Iso639Data.getType(language);
                assertRelation("Language ok? " + language + " in " + file, true, ALLOWED_LANGUAGE_TYPES, TestFmwkPlus.CONTAINS, type);
            }
        }
    }

    public void TestConsistency() {
        LanguageTagParser ltp = new LanguageTagParser();
        SupplementalDataInfo supplementalDataInfo = testInfo.getSupplementalDataInfo();
        Set<String> defaultContentLocales = supplementalDataInfo.getDefaultContentLocales();
        Map<String, String> likelySubtags = supplementalDataInfo.getLikelySubtags();

        for (String locale : testInfo.getCldrFactory().getAvailable()) {
            if (locale.equals("root")) {
                continue;
            }
            ltp.set(locale);
            boolean isDefaultContent = defaultContentLocales.contains(locale);
            boolean hasScript = !ltp.getScript().isEmpty();
            boolean hasRegion = !ltp.getRegion().isEmpty();
            String language = ltp.getLanguage();
            String maximized = likelySubtags.get(language);
            boolean hasLikelySubtag = maximized != null;

            // verify that the parent locales are consistent with the default locales, for scripts
            // that is, if zh-Hant has a parent of root, then it is not the default content locale, and vice versa

            if (hasScript && !hasRegion) {
                boolean parentIsRoot = "root".equals(supplementalDataInfo.getExplicitParentLocale(locale));
                if (parentIsRoot == isDefaultContent) {
                    errln("Inconsistency between parentLocales and defaultContents: " + locale
                        + (parentIsRoot ? " +" : " -") + "parentIsRoot"
                        + (isDefaultContent ? " +" : " -") + "isDefaultContent");
                }

                // we'd better have a separate likelySubtag
                if (parentIsRoot && !hasLikelySubtag) {
                    errln("Missing likely subtags for: " + locale + " "
                        + TestInheritance.suggestLikelySubtagFor(locale));
                }
            }

            // verify that likelySubtags has all the languages

            if (!hasScript && !hasRegion) {
                if (!hasLikelySubtag) {
                    errln("Missing likely subtags for: " + locale + " "
                        + TestInheritance.suggestLikelySubtagFor(locale));
                }
            }
        }
    }

    public void TestCanonicalizer() {
        LanguageTagCanonicalizer canonicalizer = new LanguageTagCanonicalizer();
        String[][] tests = {
            { "iw", "he" },
            { "no-YU", "nb_RS" },
            { "no", "nb" },
            { "eng-833", "en_IM" },
            { "mo", "ro_MD" },
            { "mo_Cyrl", "ro_Cyrl_MD" },
            { "mo_US", "ro_US" },
            { "mo_Cyrl_US", "ro_Cyrl_US" },
            { "sh", "sr_Latn" },
            { "sh_US", "sr_Latn_US" },
            { "sh_Cyrl", "sr_Cyrl" },
            { "sh_Cyrl_US", "sr_Cyrl_US" },
            { "hy_SU", "hy_AM" },
            { "en_SU", "en_RU" },
            { "rO-cYrl-aQ", "ro_Cyrl_AQ" },
        };
        for (String[] pair : tests) {
            String actual = canonicalizer.transform(pair[0]);
            assertEquals("Canonical", pair[1], actual);
        }
    }

    public void TestBrackets() {
        String[][] tests = {
            { "language", "en", "Anglish (abc)", "en", "Anglish [abc]",
                "〖?Anglish [abc]?❬ (U.S. [ghi])❭〗〖?Anglish [abc]?❬ (Latine [def])❭〗〖?Anglish [abc]?❬ (Latine [def], U.S. [ghi])❭〗〖❬Langue: ❭?Anglish (abc)?〗" },
            { "script", "Latn", "Latine (def)", "en_Latn", "Anglish [abc] (Latine [def])",
                "〖❬Anglish [abc] (❭?Latine [def]?❬)❭〗〖❬Anglish [abc] (❭?Latine [def]?❬, U.S. [ghi])❭〗〖❬Scripte: ❭?Latine (def)?〗" },
            { "territory", "US", "U.S. (ghi)", "en_Latn_US", "Anglish [abc] (Latine [def], U.S. [ghi])",
                "〖❬Anglish [abc] (❭?U.S. [ghi]?❬)❭〗〖❬Anglish [abc] (Latine [def], ❭?U.S. [ghi]?❬)❭〗〖❬Territorie: ❭?U.S. (ghi)?〗" },
            { null, null, null, "en_US", "Anglish [abc] (U.S. [ghi])", null },
            { "variant", "foobar", "foo (jkl)", "en_foobar", "Anglish [abc] (foo [jkl])", null },
            { "key", "co", "sort (mno)", "en_foobar@co=FOO", "Anglish [abc] (foo [jkl], sort [mno]=FOO)", null },
            { "type|key", "FII|co", "sortfii (mno)", "en_foobar@co=FII", "Anglish [abc] (foo [jkl], sortfii [mno])", null },
        };
        // load up a dummy source
        SimpleXMLSource dxs = new SimpleXMLSource("xx");
        for (String[] row : tests) {
            if (row[0] == null) {
                continue;
            }
            int typeCode = CLDRFile.typeNameToCode(row[0]);
            String path = CLDRFile.getKey(typeCode, row[1]);
            dxs.putValueAtDPath(path, row[2]);
        }
        // create a cldrfile from it and test
        SimpleXMLSource root = new SimpleXMLSource("root");
        root.putValueAtDPath("//ldml/localeDisplayNames/localeDisplayPattern/localePattern", "{0} ({1})");
        root.putValueAtDPath("//ldml/localeDisplayNames/localeDisplayPattern/localeSeparator", "{0}, {1}");
        root.putValueAtDPath("//ldml/localeDisplayNames/codePatterns/codePattern[@type=\"language\"]", "Langue: {0}");
        root.putValueAtDPath("//ldml/localeDisplayNames/codePatterns/codePattern[@type=\"script\"]", "Scripte: {0}");
        root.putValueAtDPath("//ldml/localeDisplayNames/codePatterns/codePattern[@type=\"territory\"]", "Territorie: {0}");
        CLDRFile f = new CLDRFile(dxs, root);
        ExampleGenerator eg = new ExampleGenerator(f, testInfo.getEnglish(), CLDRPaths.DEFAULT_SUPPLEMENTAL_DIRECTORY);
        for (String[] row : tests) {
            if (row[0] != null) {
                int typeCode = CLDRFile.typeNameToCode(row[0]);
                String standAlone = f.getName(typeCode, row[1]);
                assertEquals("stand-alone " + row[3], row[2], standAlone);
                if (row[5] != null) {
                    String path = CLDRFile.getKey(typeCode, row[1]);
                    String example = eg.getExampleHtml(path, "?" + row[2] + "?");
                    assertEquals("example " + row[3], row[5], ExampleGenerator.simplify(example));
                }
            }
            String displayName = f.getName(row[3], true, "{0}={1}", "{0} ({1})", "{0}, {1}");
            assertEquals("locale " + row[3], row[4], displayName);
        }
    }

    public void TestLocaleNamePattern() {
        assertEquals("Locale name", "Chinese", testInfo.getEnglish().getName("zh"));
        assertEquals("Locale name", "Chinese (United States)", testInfo.getEnglish().getName("zh-US"));
        assertEquals("Locale name", "Chinese (Arabic, United States)", testInfo.getEnglish().getName("zh-Arab-US"));
        CLDRFile japanese = testInfo.getCldrFactory().make("ja", true);
        assertEquals("Locale name", "中国語", japanese.getName("zh"));
        assertEquals("Locale name", "中国語 (アメリカ合衆国)", japanese.getName("zh-US"));
        assertEquals("Locale name", "中国語 (アラビア文字\u3001アメリカ合衆国)", japanese.getName("zh-Arab-US"));
    }

    public void TestExtendedLanguage() {
        assertEquals("Extended language translation", "Simplified Chinese", testInfo.getEnglish().getName("zh_Hans"));
        assertEquals("Extended language translation", "Simplified Chinese (Singapore)",
            testInfo.getEnglish().getName("zh_Hans_SG"));
        assertEquals("Extended language translation", "American English", testInfo.getEnglish().getName("en-US"));
        assertEquals("Extended language translation", "American English (Arabic)",
            testInfo.getEnglish().getName("en-Arab-US"));
    }

    public void TestNarrowEnough() {
        BreakIterator bi = BreakIterator.getCharacterInstance(ULocale.ENGLISH);
        assertEquals("Narrow Enough", 1, CheckDates.isNarrowEnough("a", bi));
        assertEquals("Narrow Enough", 2, CheckDates.isNarrowEnough("ab", bi));
        assertEquals("Narrow Enough", 2, CheckDates.isNarrowEnough("abc", bi));
        assertEquals("Narrow Enough", 4, CheckDates.isNarrowEnough("a\u0308b\u0308", bi));
        assertEquals("Narrow Enough", 4, CheckDates.isNarrowEnough("a\u0308b\u0308c\u0308", bi));
    }
}
