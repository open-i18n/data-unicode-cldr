package org.unicode.cldr.unittest;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.VoteResolver.Organization;

import com.ibm.icu.dev.test.TestFmwk;

public class StandardCodesTest extends TestFmwk {
    public static void main(String[] args) {
        new StandardCodesTest().run(args);
    }

    static final StandardCodes sc = StandardCodes.make();

    static final TestInfo testInfo = TestInfo.getInstance();

    public void TestCoverage() {
        EnumSet<Organization> missing = EnumSet.noneOf(Organization.class);
        Set<String> extraOrgs = new TreeSet<String>();
        for (String org : sc.getLocaleCoverageOrganizations()) {
            extraOrgs.add(org.toLowerCase());
        }
        for (Organization org : Organization.values()) {
            // Sun ; ar ; modern
            extraOrgs.remove(org.toString().toLowerCase());
            if (!sc.getLocaleCoverageOrganizations().contains(org.toString())) {
                missing.add(org);
                continue;
            }
            for (String locale : sc.getLocaleCoverageLocales(org.toString())) {
                String name = locale.equals("*") ? "ALL" : testInfo.getEnglish().getName(locale);
                logln(org + "\t;\t" + locale + "\t;\t" + sc.getLocaleCoverageLevel(org.toString(), locale) + "\t;\t" + name);
            }
        }
        for (Organization org : missing) {
            errln("Organization missing Locales.txt information " + org);
        }
        for (String org : extraOrgs) {
            errln("Organization in Locales.txt but not in Organization enum: " + org);
        }
    }

    public void TestGetLocaleCoverageLocales() {
        Factory cldrFactory = TestCLDRFile.getAllFactory();
        Set<String> availableLocales = cldrFactory.getAvailable();
        for (Organization org : Organization.values()) {
            Set<String> locs;
            try {
                locs = sc.getLocaleCoverageLocales(org.toString(), EnumSet.of(Level.MODERATE, Level.MODERN));
                for (String loc : locs) {
                    if (loc.equals("*")) continue;
                    if (!availableLocales.contains(loc)) {
                        warnln("Locales.txt:\t" + loc + " (" + CLDRLocale.getInstance(loc).getDisplayName() + ")"
                            + " for " + org + " isn't in CLDR (common/main or seed).");
                    }
                }
            } catch (NullPointerException npe) {
                errln("NPE trying to get coverage for " + org);
                continue;
            }
            // logln(org + " : " + locs.toString());
        }
    }

    public void TestAllEnums() {
        for (String type : sc.getAvailableTypes()) {
            for (String code : sc.getAvailableCodes(type)) {
                sc.getFullData(type, code);
            }
        }
    }
}
