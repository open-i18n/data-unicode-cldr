/*
 ******************************************************************************************
 * Copyright (C) 2009-2014, Google, Inc.; International Business Machines Corporation and *
 * others. All Rights Reserved.                                                           *
 ******************************************************************************************
 */

package org.unicode.cldr.unittest;

import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.SupplementalDataInfo;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.impl.Row.R4;
import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.LocaleMatcher.LanguageMatcherData;
import com.ibm.icu.util.LocalePriorityList;
import com.ibm.icu.util.ULocale;

/**
 * Test the LocaleMatcher.
 * 
 * @author markdavis
 */
@SuppressWarnings("deprecation")
public class LocaleMatcherTest extends TestFmwk {

    private static final CLDRConfig CONFIG = CLDRConfig.getInstance();

    static final SupplementalDataInfo INFO = CONFIG.getSupplementalDataInfo();

    private static final ULocale ZH_MO = new ULocale("zh_MO");
    private static final ULocale ZH_HK = new ULocale("zh_HK");
    static LanguageMatcherData LANGUAGE_MATCHER_DATA = new LanguageMatcherData();

    static {
        for (R4<String, String, Integer, Boolean> foo : INFO.getLanguageMatcherData("written")) {
            LANGUAGE_MATCHER_DATA.addDistance(foo.get0(), foo.get1(), foo.get2(), foo.get3());
        }
        LANGUAGE_MATCHER_DATA.freeze();
    }

    private LocaleMatcher newLocaleMatcher(LocalePriorityList build) {
        return new LocaleMatcher(build, LANGUAGE_MATCHER_DATA);
    }

    private LocaleMatcher newLocaleMatcher(LocalePriorityList build, LanguageMatcherData data) {
        return new LocaleMatcher(build, data == null ? LANGUAGE_MATCHER_DATA : data);
    }

    private LocaleMatcher newLocaleMatcher(LocalePriorityList lpl, LanguageMatcherData data, double d) {
        return new LocaleMatcher(lpl, data == null ? LANGUAGE_MATCHER_DATA : data, d);
    }

    private LocaleMatcher newLocaleMatcher(String string) {
        return new LocaleMatcher(LocalePriorityList.add(string).build(), LANGUAGE_MATCHER_DATA);
    }

    // public LocaleMatcher(LocalePriorityList languagePriorityList,
    // LocaleMatcherData matcherData, double threshold)

    public static void main(String[] args) throws Exception {
        new LocaleMatcherTest().run(args);
    }

    public void testParentLocales() {
        assertCloser("es_AR", "es_419", "es_ES");
        assertCloser("es_AR", "es_419", "es");

        assertCloser("es_AR", "es_MX", "es");
        assertCloser("es_AR", "es_MX", "es");

        assertCloser("en_AU", "en_GB", "en_US");
        assertCloser("en_AU", "en_GB", "en");

        assertCloser("en_AU", "en_NZ", "en_US");
        assertCloser("en_AU", "en_NZ", "en");

        assertCloser("pt_AO", "pt_PT", "pt_BR");
        assertCloser("pt_AO", "pt_PT", "pt");

        assertCloser("zh_HK", "zh_MO", "zh_TW");
        assertCloser("zh_HK", "zh_MO", "zh_CN");
        assertCloser("zh_HK", "zh_MO", "zh");
    }

    private void assertCloser(String a, String closer, String further) {
        LocaleMatcher matcher = newLocaleMatcher(further + ", " + closer);
        assertEquals("test " + a + " is closer to " + closer + " than to " + further, new ULocale(closer), matcher.getBestMatch(a));
        matcher = newLocaleMatcher(closer + ", " + further);
        assertEquals("test " + a + " is closer to " + closer + " than to " + further, new ULocale(closer), matcher.getBestMatch(a));
    }

    //    public void testParentLocales() {
    //        // find all the regions that have a closer relation because of an explicit parent
    //        Set<String> explicitParents = new HashSet<>(INFO.getExplicitParents());
    //        explicitParents.remove("root");
    //        Set<String> otherParents = new HashSet<>(INFO.getExplicitParents());
    //        for (String locale : explicitParents) {
    //            while (true) {
    //                locale = LocaleIDParser.getParent(locale);
    //                if (locale == null || locale.equals("root")) {
    //                    break;
    //                }
    //                otherParents.add(locale);
    //            }
    //        }
    //        otherParents.remove("root");
    //
    //        for (String locale : CONFIG.getCldrFactory().getAvailable()) {
    //            String parentId = LocaleIDParser.getParent(locale);
    //            String parentIdSimple = LocaleIDParser.getSimpleParent(locale);
    //            if (!explicitParents.contains(parentId) && !otherParents.contains(parentIdSimple)) {
    //                continue;
    //            }
    //            System.out.println(locale + "\t" + CONFIG.getEnglish().getName(locale) + "\t" + parentId + "\t" + parentIdSimple);
    //        }
    //    }

    public void testChinese() {
        LocaleMatcher matcher = newLocaleMatcher("zh_CN, zh_TW, iw");
        ULocale taiwanChinese = new ULocale("zh_TW");
        ULocale chinaChinese = new ULocale("zh_CN");
        assertEquals("zh_CN, zh_TW, iw;", taiwanChinese, matcher.getBestMatch("zh_Hant_TW"));
        assertEquals("zh_CN, zh_TW, iw;", taiwanChinese, matcher.getBestMatch("zh_Hant"));
        assertEquals("zh_CN, zh_TW, iw;", taiwanChinese, matcher.getBestMatch("zh_TW"));
        assertEquals("zh_CN, zh_TW, iw;", chinaChinese, matcher.getBestMatch("zh_Hans_CN"));
        assertEquals("zh_CN, zh_TW, iw;", chinaChinese, matcher.getBestMatch("zh_CN"));
        assertEquals("zh_CN, zh_TW, iw;", chinaChinese, matcher.getBestMatch("zh"));
        assertEquals("zh_CN, zh_TW, iw;", taiwanChinese, matcher.getBestMatch("zh_Hant_HK"));
    }

    public void testenGB() {
        final LocaleMatcher matcher = newLocaleMatcher("fr, en, en_GB, es_MX, es_419, es");
        assertEquals("en_GB", matcher.getBestMatch("en_NZ").toString());
        assertEquals("es", matcher.getBestMatch("es_ES").toString());
        assertEquals("es_419", matcher.getBestMatch("es_AR").toString());
        assertEquals("es_MX", matcher.getBestMatch("es_MX").toString());
    }

    public void testFallbacks() {
        LocalePriorityList lpl = LocalePriorityList.add("en, hi").build();
        final LocaleMatcher matcher = newLocaleMatcher(lpl, null, 0.09);
        assertEquals("hi", matcher.getBestMatch("sa").toString());
    }

    public void testOverrideData() {
        double threshold = 0.05;
        LanguageMatcherData localeMatcherData = new LanguageMatcherData()
            .addDistance("br", "fr", 10, true)
            .addDistance("es", "cy", 10, true);
        logln(localeMatcherData.toString());

        final LocaleMatcher matcher = newLocaleMatcher(
            LocalePriorityList
                .add(ULocale.ENGLISH)
                .add(ULocale.FRENCH)
                .add(ULocale.UK)
                .build(), localeMatcherData, threshold);
        logln(matcher.toString());

        assertEquals(ULocale.FRENCH, matcher.getBestMatch(new ULocale("br")));
        assertEquals(ULocale.ENGLISH, matcher.getBestMatch(new ULocale("es"))); // one
        // way
    }

    public void testBasics() {
        final LocaleMatcher matcher = newLocaleMatcher(LocalePriorityList.add(ULocale.FRENCH).add(ULocale.UK)
            .add(ULocale.ENGLISH).build());
        logln(matcher.toString());

        assertEquals(ULocale.UK, matcher.getBestMatch(ULocale.UK));
        assertEquals(ULocale.ENGLISH, matcher.getBestMatch(ULocale.US));
        assertEquals(ULocale.FRENCH, matcher.getBestMatch(ULocale.FRANCE));
        assertEquals(ULocale.FRENCH, matcher.getBestMatch(ULocale.JAPAN));
    }

    public void testFallback() {
        // check that script fallbacks are handled right
        final LocaleMatcher matcher = newLocaleMatcher("zh_CN, zh_TW, iw");
        assertEquals(new ULocale("zh_TW"), matcher.getBestMatch("zh_Hant"));
        assertEquals(new ULocale("zh_CN"), matcher.getBestMatch("zh"));
        assertEquals(new ULocale("zh_CN"), matcher.getBestMatch("zh_Hans_CN"));
        assertEquals(new ULocale("zh_TW"), matcher.getBestMatch("zh_Hant_HK"));
        assertEquals(new ULocale("he"), matcher.getBestMatch("iw_IT"));
    }

    public void testSpecials() {
        // check that nearby languages are handled
        final LocaleMatcher matcher = newLocaleMatcher("en, fil, ro, nn");
        assertEquals(new ULocale("fil"), matcher.getBestMatch("tl"));
        assertEquals(new ULocale("ro"), matcher.getBestMatch("mo"));
        assertEquals(new ULocale("nn"), matcher.getBestMatch("nb"));
        // make sure default works
        assertEquals(new ULocale("en"), matcher.getBestMatch("ja"));
    }

    public void testRegionalSpecials() {
        // verify that en_AU is closer to en_GB than to en (which is en_US)
        final LocaleMatcher matcher = newLocaleMatcher("en, en_GB, es, es_419");
        assertEquals("es_MX in {en, en_GB, es, es_419}", new ULocale("es_419"), matcher.getBestMatch("es_MX"));
        assertEquals("en_AU in {en, en_GB, es, es_419}", new ULocale("en_GB"), matcher.getBestMatch("en_AU"));
        assertEquals("es_ES in {en, en_GB, es, es_419}", new ULocale("es"), matcher.getBestMatch("es_ES"));
    }

    public void testHK() {
        // HK and MO are closer to each other for Hant than to TW
        final LocaleMatcher matcher = newLocaleMatcher("zh, zh_TW, zh_MO");
        assertEquals("zh_HK in {zh, zh_TW, zh_MO}", ZH_MO, matcher.getBestMatch("zh_HK"));
        final LocaleMatcher matcher2 = newLocaleMatcher("zh, zh_TW, zh_HK");
        assertEquals("zh_MO in {zh, zh_TW, zh_HK}", ZH_HK, matcher2.getBestMatch("zh_MO"));
    }

    public void TestLocaleMatcherCoverage() {
        // Add tests for better code coverage
        LocaleMatcher matcher = newLocaleMatcher(LocalePriorityList.add(null, 0).build(), null);
        logln(matcher.toString());

        LanguageMatcherData data = new LanguageMatcherData();

        LanguageMatcherData clone = data.cloneAsThawed();

        if (clone.equals(data)) {
            errln("Error cloneAsThawed() is equal.");
        }

        if (data.isFrozen()) {
            errln("Error LocaleMatcherData is frozen!");
        }
    }

    private void assertEquals(Object expected, Object string) {
        assertEquals("", expected, string);
    }

    private void assertNull(Object bestMatch) {
        assertNull("", bestMatch);
    }

    public void testEmpty() {
        final LocaleMatcher matcher = newLocaleMatcher("");
        assertNull(matcher.getBestMatch(ULocale.FRENCH));
    }

    static final ULocale ENGLISH_CANADA = new ULocale("en_CA");

    public void testMatch_exact() {
        assertEquals(1.0,
            LocaleMatcher.match(ENGLISH_CANADA, ENGLISH_CANADA));
    }

    public void testMatch_none() {
        double match = LocaleMatcher.match(
            new ULocale("ar_MK"),
            ENGLISH_CANADA);
        assertTrue("Actual < 0: " + match, 0 <= match);
        assertTrue("Actual > 0.15 (~ language + script distance): " + match, 0.2 > match);
    }

    public void testMatch_matchOnMazimized() {
        ULocale undTw = new ULocale("und_TW");
        ULocale zhHant = new ULocale("zh_Hant");
        double matchZh = LocaleMatcher.match(undTw, new ULocale("zh"));
        double matchZhHant = LocaleMatcher.match(undTw, zhHant);
        assertTrue("und_TW should be closer to zh_Hant (" + matchZhHant +
            ") than to zh (" + matchZh + ")",
            matchZh < matchZhHant);
        double matchEnHantTw = LocaleMatcher.match(new ULocale("en_Hant_TW"),
            zhHant);
        assertTrue("zh_Hant should be closer to und_TW (" + matchZhHant +
            ") than to en_Hant_TW (" + matchEnHantTw + ")",
            matchEnHantTw < matchZhHant);
        assertTrue("zh should be closer to und_TW (" + matchZh +
            ") than to en_Hant_TW (" + matchEnHantTw + ")",
            matchEnHantTw < matchZh);
    }

    public void testMatchGrandfatheredCode() {
        final LocaleMatcher matcher = newLocaleMatcher("fr, i_klingon, en_Latn_US");
        assertEquals("en_Latn_US", matcher.getBestMatch("en_GB_oed").toString());
        // assertEquals("tlh", matcher.getBestMatch("i_klingon").toString());
    }

    public void testGetBestMatchForList_exactMatch() {
        final LocaleMatcher matcher = newLocaleMatcher("fr, en_GB, ja, es_ES, es_MX");
        assertEquals("ja", matcher.getBestMatch("ja, de").toString());
    }

    public void testGetBestMatchForList_simpleVariantMatch() {
        final LocaleMatcher matcher = newLocaleMatcher("fr, en_GB, ja, es_ES, es_MX");
        // Intentionally avoiding a perfect_match or two candidates for variant
        // matches.
        assertEquals("en_GB", matcher.getBestMatch("de, en_US").toString());
        // Fall back.
        assertEquals("fr", matcher.getBestMatch("de, zh").toString());
    }

    public void testGetBestMatchForList_matchOnMaximized() {
        final LocaleMatcher matcher = newLocaleMatcher("en, ja");
        // final LocaleMatcher matcher =
        // newLocaleMatcher("fr, en, ja, es_ES, es_MX");
        // Check that if the preference is maximized already, it works as well.
        assertEquals("Match for ja_Jpan_JP (maximized already)",
            "ja", matcher.getBestMatch("ja_Jpan_JP, en-AU").toString());
        if (true)
            return;
        // ja_JP matches ja on likely subtags, and it's listed first, thus it
        // wins over
        // thus it wins over the second preference en_GB.
        assertEquals("Match for ja_JP, with likely region subtag",
            "ja", matcher.getBestMatch("ja_JP, en_US").toString());
        // Check that if the preference is maximized already, it works as well.
        assertEquals("Match for ja_Jpan_JP (maximized already)",
            "ja", matcher.getBestMatch("ja_Jpan_JP, en_US").toString());
    }

    public void testGetBestMatchForList_noMatchOnMaximized() {
        // Regression test for http://b/5714572 .
        final LocaleMatcher matcher = newLocaleMatcher("en, de, fr, ja");
        // de maximizes to de_DE. Pick the exact match for the secondary
        // language instead.
        assertEquals("fr", matcher.getBestMatch("de_CH, fr").toString());
    }

    public void testBestMatchForTraditionalChinese() {
        // Scenario: An application that only supports Simplified Chinese (and
        // some other languages),
        // but does not support Traditional Chinese. zh_Hans_CN could be
        // replaced with zh_CN, zh, or
        // zh_Hans, it wouldn't make much of a difference.
        final LocaleMatcher matcher = newLocaleMatcher("fr, zh_Hans_CN, en_US");

        // The script distance (simplified vs. traditional Han) is considered
        // small enough
        // to be an acceptable match. The regional difference is considered
        // almost insignificant.
        assertEquals("zh_Hans_CN", matcher.getBestMatch("zh_TW").toString());
        assertEquals("zh_Hans_CN", matcher.getBestMatch("zh_Hant").toString());

        // For geo_political reasons, you might want to avoid a zh_Hant ->
        // zh_Hans match.
        // In this case, if zh_TW, zh_HK or a tag starting with zh_Hant is
        // requested, you can
        // change your call to getBestMatch to include a 2nd language
        // preference.
        // "en" is a better match since its distance to "en_US" is closer than
        // the distance
        // from "zh_TW" to "zh_CN" (script distance).
        assertEquals("en_US", matcher.getBestMatch("zh_TW, en").toString());
        assertEquals("en_US", matcher.getBestMatch("zh_Hant_CN, en").toString());
        assertEquals("zh_Hans_CN", matcher.getBestMatch("zh_Hans, en").toString());
    }

    public void testUndefined() {
        // When the undefined language doesn't match anything in the list,
        // getBestMatch returns
        // the default, as usual.
        LocaleMatcher matcher = newLocaleMatcher("it,fr");
        assertEquals("it", matcher.getBestMatch("und").toString());

        // When it *does* occur in the list, BestMatch returns it, as expected.
        matcher = newLocaleMatcher("it,und");
        assertEquals("und", matcher.getBestMatch("und").toString());

        // The unusual part:
        // max("und") = "en_Latn_US", and since matching is based on maximized
        // tags, the undefined
        // language would normally match English. But that would produce the
        // counterintuitive results
        // that getBestMatch("und", LocaleMatcher("it,en")) would be "en", and
        // getBestMatch("en", LocaleMatcher("it,und")) would be "und".
        //
        // To avoid that, we change the matcher's definitions of max
        // (AddLikelySubtagsWithDefaults)
        // so that max("und")="und". That produces the following, more desirable
        // results:
        matcher = newLocaleMatcher("it,en");
        assertEquals("it", matcher.getBestMatch("und").toString());
        matcher = newLocaleMatcher("it,und");
        assertEquals("it", matcher.getBestMatch("en").toString());
    }

    // public void testGetBestMatch_emptyList() {
    // final LocaleMatcher matcher = newLocaleMatcher(
    // new LocalePriorityList(new HashMap()));
    // assertNull(matcher.getBestMatch(ULocale.ENGLISH));
    // }

    public void testGetBestMatch_googlePseudoLocales() {
        // Google pseudo locales are primarily based on variant subtags.
        // See http://sites/intl_eng/pseudo_locales.
        // (See below for the region code based fall back options.)
        final LocaleMatcher matcher = newLocaleMatcher(
            "fr, pt");
        assertEquals("fr", matcher.getBestMatch("de").toString());
        assertEquals("fr", matcher.getBestMatch("en_US").toString());
        assertEquals("fr", matcher.getBestMatch("en").toString());
        assertEquals("pt", matcher.getBestMatch("pt_BR").toString());
    }

    public void testGetBestMatch_regionDistance() {
        LocaleMatcher matcher = newLocaleMatcher("es_AR, es");
        assertEquals("es_AR", matcher.getBestMatch("es_MX").toString());

        matcher = newLocaleMatcher("fr, en, en_CA");
        assertEquals("en_CA", matcher.getBestMatch("en_GB").toString());

        matcher = newLocaleMatcher("de_AT, de_DE, de_CH");
        assertEquals("de_DE", matcher.getBestMatch("de").toString());
    }

    /**
     * If all the base languages are the same, then each sublocale matches
     * itself most closely
     */
    public void testExactMatches() {
        String lastBase = "";
        TreeSet<ULocale> sorted = new TreeSet<ULocale>();
        for (ULocale loc : ULocale.getAvailableLocales()) {
            String language = loc.getLanguage();
            if (!lastBase.equals(language)) {
                check(sorted);
                sorted.clear();
                lastBase = language;
            }
            sorted.add(loc);
        }
        check(sorted);
    }

    private void check(Set<ULocale> sorted) {
        if (sorted.isEmpty()) {
            return;
        }
        check2(sorted);
        ULocale first = sorted.iterator().next();
        ULocale max = ULocale.addLikelySubtags(first);
        sorted.add(max);
        check2(sorted);
    }

    /**
     * @param sorted
     */
    private void check2(Set<ULocale> sorted) {
        // TODO Auto-generated method stub
        logln("Checking: " + sorted);
        LocaleMatcher matcher = newLocaleMatcher(
            LocalePriorityList.add(
                sorted.toArray(new ULocale[sorted.size()]))
                .build());
        for (ULocale loc : sorted) {
            String stringLoc = loc.toString();
            assertEquals(stringLoc, matcher.getBestMatch(stringLoc).toString());
        }
    }

    // public void testComputeDistance_monkeyTest() {
    // RegionCode[] codes = RegionCode.values();
    // Random random = new Random();
    // for (int i = 0; i < 1000; ++i) {
    // RegionCode x = codes[random.nextInt(codes.length)];
    // RegionCode y = codes[random.nextInt(codes.length)];
    // double d = LocaleMatcher.getRegionDistance(x, y, null, null);
    // if (x == RegionCode.ZZ || y == RegionCode.ZZ) {
    // assertEquals(LocaleMatcher.REGION_DISTANCE, d);
    // } else if (x == y) {
    // assertEquals(0.0, d);
    // } else {
    // assertTrue(d > 0);
    // assertTrue(d <= LocaleMatcher.REGION_DISTANCE);
    // }
    // }
    // }

    public void testGetBestMatchForList_matchOnMaximized2() {
        if (logKnownIssue("Cldrbug:8215", "better matching with regional variants")) {
            return;
        }
        final LocaleMatcher matcher = newLocaleMatcher("fr, en-GB, ja, es-ES, es-MX");
        // ja-JP matches ja on likely subtags, and it's listed first, thus it wins over
        // thus it wins over the second preference en-GB.
        assertEquals("Match for ja-JP, with likely region subtag",
            "ja", matcher.getBestMatch("ja-JP, en-GB").toString());
        // Check that if the preference is maximized already, it works as well.
        assertEquals("Match for ja-Jpan-JP (maximized already)",
            "ja", matcher.getBestMatch("ja-Jpan-JP, en-GB").toString());
    }

    public void testGetBestMatchForList_closeEnoughMatchOnMaximized() {
        if (logKnownIssue("Cldrbug:8215", "better matching with regional variants")) {
            return;
        }
        final LocaleMatcher matcher = newLocaleMatcher("en-GB, en, de, fr, ja");
        assertEquals("de", matcher.getBestMatch("de-CH, fr").toString());
        assertEquals("en", matcher.getBestMatch("en-US, ar, nl, de, ja").toString());
    }

    public void testGetBestMatchForPortuguese() {

        if (logKnownIssue("Cldrbug:8215", "better matching with regional variants")) {
            return;
        }

        final LocaleMatcher withPTExplicit = newLocaleMatcher("pt_PT, pt_BR, es, es_419");
        final LocaleMatcher withPTImplicit = newLocaleMatcher("pt_PT, pt, es, es_419");
        // Could happen because "pt_BR" is a tier_1 language and "pt_PT" is tier_2.

        final LocaleMatcher withoutPT = newLocaleMatcher("pt_BR, es, es_419");
        // European user who prefers Spanish over Brazillian Portuguese as a fallback.

        assertEquals("pt_PT", withPTExplicit.getBestMatch("pt_PT, es, pt").toString());
        assertEquals("pt_PT", withPTImplicit.getBestMatch("pt_PT, es, pt").toString());
        assertEquals("es", withoutPT.getBestMatch("pt_PT, es, pt").toString());

        // Brazillian user who prefers South American Spanish over European Portuguese as a fallback.
        // The asymmetry between this case and above is because it's "pt_PT" that's missing between the
        // matchers as "pt_BR" is a much more common language.
        assertEquals("pt_BR", withPTExplicit.getBestMatch("pt, es_419, pt_PT").toString());
        assertEquals("pt", withPTImplicit.getBestMatch("pt, es_419, pt_PT").toString());
        assertEquals("pt_BR", withoutPT.getBestMatch("pt, es_419, pt_PT").toString());

        // Code that adds the user's country can get "pt_US" for a user's language.
        // That should fall back to "pt_BR".
        assertEquals("pt_BR", withPTExplicit.getBestMatch("pt_US, pt_PT").toString());
        assertEquals("pt", withPTImplicit.getBestMatch("pt_US, pt_PT").toString());
    }

    public void testVariantWithScriptMatch() {
        if (logKnownIssue("Cldrbug:8215", "better matching with regional variants")) {
            return;
        }
        final LocaleMatcher matcher = newLocaleMatcher("fr, en, sv");
        assertEquals("en", matcher.getBestMatch("en-GB").toString());
        assertEquals("en", matcher.getBestMatch("en-GB, sv").toString());
    }
}
