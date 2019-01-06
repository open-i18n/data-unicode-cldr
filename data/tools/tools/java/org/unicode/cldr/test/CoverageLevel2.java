package org.unicode.cldr.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.unittest.TestAll;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.Builder;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.RegexLookup;
import org.unicode.cldr.util.RegexLookup.Finder;
import org.unicode.cldr.util.RegexLookup.RegexFinder;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.CoverageLevelInfo;
import org.unicode.cldr.util.SupplementalDataInfo.CoverageVariableInfo;
import org.unicode.cldr.util.Timer;

import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.util.Output;
import com.ibm.icu.util.ULocale;

public class CoverageLevel2 {

    // To modify the results, see /cldr/common/supplemental/coverageLevels.xml

    private RegexLookup<Level> lookup = null;
    private static StandardCodes sc = StandardCodes.make();

    enum SetMatchType {
        Target_Language, Target_Scripts, Target_Territories, Target_TimeZones, Target_Currencies, Target_Plurals, Calendar_List
    }

    private static class LocaleSpecificInfo {
        CoverageVariableInfo cvi;
        String targetLanguage;
    }

    final LocaleSpecificInfo myInfo = new LocaleSpecificInfo();

    /**
     * We define a regex finder for use in the lookup. It has extra tests based on the ci value and the cvi value,
     * duplicating
     * what was in SupplementalDataInfo. It uses the sets instead of converting to regex strings.
     * 
     * @author markdavis
     * 
     */
    public static class MyRegexFinder extends RegexFinder {
        final private SetMatchType additionalMatch;
        final private CoverageLevelInfo ci;

        public MyRegexFinder(String pattern, String additionalMatch, CoverageLevelInfo ci) {
            super(pattern);
            // remove the ${ and the }, and change - to _.
            this.additionalMatch = additionalMatch == null ? null : SetMatchType.valueOf(additionalMatch.substring(2,
                additionalMatch.length() - 1).replace('-', '_'));
            this.ci = ci;
        }

        @Override
        public boolean find(String item, Object context) {
            LocaleSpecificInfo localeSpecificInfo = (LocaleSpecificInfo) context;
            // Modified the logic to handle the case where we want specific languages and specific territories.
            // Any match in language script or territory will succeed when multiple items are present.
            boolean lstOK = false;
            if (ci.inLanguageSet == null && ci.inScriptSet == null && ci.inTerritorySet == null) {
                lstOK = true;
            } else if (ci.inLanguageSet != null
                && ci.inLanguageSet.contains(localeSpecificInfo.targetLanguage)) {
                lstOK = true;
            } else if (ci.inScriptSet != null
                && CollectionUtilities.containsSome(ci.inScriptSet, localeSpecificInfo.cvi.targetScripts)) {
                lstOK = true;
            } else if (ci.inTerritorySet != null
                && CollectionUtilities.containsSome(ci.inTerritorySet, localeSpecificInfo.cvi.targetTerritories)) {
                lstOK = true;
            }

            if (!lstOK) {
                return false;
            }

            boolean result = super.find(item, context); // also sets matcher in RegexFinder
            if (!result) {
                return false;
            }
            if (additionalMatch != null) {
                String groupMatch = matcher.group(1);
                // we match on a group, so get the right one
                switch (additionalMatch) {
                case Target_Language:
                    return localeSpecificInfo.targetLanguage.equals(groupMatch);
                case Target_Scripts:
                    return localeSpecificInfo.cvi.targetScripts.contains(groupMatch);
                case Target_Territories:
                    return localeSpecificInfo.cvi.targetTerritories.contains(groupMatch);
                case Target_TimeZones:
                    return localeSpecificInfo.cvi.targetTimeZones.contains(groupMatch);
                case Target_Currencies:
                    return localeSpecificInfo.cvi.targetCurrencies.contains(groupMatch);
                    // For Target_Plurals, we have to account for the fact that the @count= part might not be in the
                    // xpath, so we shouldn't reject the match because of that. ( i.e. The regex is usually
                    // ([@count='${Target-Plurals}'])?
                case Target_Plurals:
                    return (groupMatch == null ||
                        groupMatch.length() == 0 || localeSpecificInfo.cvi.targetPlurals.contains(groupMatch));
                case Calendar_List:
                    return localeSpecificInfo.cvi.calendars.contains(groupMatch);
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    private CoverageLevel2(SupplementalDataInfo sdi, String locale) {
        myInfo.targetLanguage = new LanguageTagParser().set(locale).getLanguage();
        myInfo.cvi = sdi.getCoverageVariableInfo(myInfo.targetLanguage);
        lookup = sdi.getCoverageLookup();
    }

    /**
     * get an instance, using CldrUtility.SUPPLEMENTAL_DIRECTORY
     * 
     * @param locale
     * @return
     * @deprecated Don't use this. call the version which takes a SupplementalDataInfo as an argument.
     * @see #getInstance(SupplementalDataInfo, String)
     * @see CldrUtility#SUPPLEMENTAL_DIRECTORY
     */
    public static CoverageLevel2 getInstance(String locale) {
        return new CoverageLevel2(SupplementalDataInfo.getInstance(), locale);
    }

    public static CoverageLevel2 getInstance(SupplementalDataInfo sdi, String locale) {
        return new CoverageLevel2(sdi, locale);
    }

    public Level getLevel(String path) {
        if (path == null) {
            return Level.UNDETERMINED;
        }
        synchronized (lookup) { // synchronize on the class, since the Matchers are changed during the matching process
            Level result;
            if (false) { // for testing
                Output<String[]> checkItems = new Output();
                Output<Finder> matcherFound = new Output<Finder>();
                List<String> failures = new ArrayList();
                result = lookup.get(path, myInfo, checkItems, matcherFound, failures);
                for (String s : failures) {
                    System.out.println(s);
                }
            } else {
                result = lookup.get(path, myInfo, null);
            }
            return result == null ? Level.OPTIONAL : result;
        }
    }

    public int getIntLevel(String path) {
        return getLevel(path).getLevel();
    }

    public static Level getRequiredLevel(String localeID, Map<String, String> options) {
        Level result;
        // see if there is an explicit level
        String localeType = options.get("CoverageLevel.requiredLevel");
        if (localeType != null) {
            result = Level.get(localeType);
            if (result != Level.UNDETERMINED) {
                return result;
            }
        }
        // otherwise, see if there is an organization level
        return sc.getLocaleCoverageLevel(options.get("CoverageLevel.localeType"), localeID);
    }

    public static void main(String[] args) {
        // quick test
        // TODO convert to unit test
        CoverageLevel2 cv2 = CoverageLevel2.getInstance("de");
        ULocale uloc = new ULocale("de");
        TestInfo testInfo = TestAll.TestInfo.getInstance();
        SupplementalDataInfo supplementalDataInfo2 = testInfo.getSupplementalDataInfo();
        CLDRFile englishPaths1 = testInfo.getEnglish();
        Set<String> englishPaths = Builder.with(new TreeSet<String>()).addAll(englishPaths1).get();

        Timer timer = new Timer();
        timer.start();
        for (String path : englishPaths) {
            int oldLevel = supplementalDataInfo2.getCoverageValueOld(path, uloc);
        }
        long oldTime = timer.getDuration();
        System.out.println(timer.toString(1));

        timer.start();
        for (String path : englishPaths) {
            int newLevel = cv2.getIntLevel(path);
        }
        System.out.println(timer.toString(1, oldTime));

        for (String path : englishPaths) {
            int newLevel = cv2.getIntLevel(path);
            int oldLevel = supplementalDataInfo2.getCoverageValueOld(path, uloc);
            if (newLevel != oldLevel) {
                newLevel = cv2.getIntLevel(path);
                System.out.println(oldLevel + "\t" + newLevel + "\t" + path);
            }
        }
    }
}
