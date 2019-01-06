/*
 *******************************************************************************
 * Copyright (C) 1996-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package org.unicode.cldr.test;

import java.util.List;
import java.util.Map;

import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.InternalCldrException;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;
import org.unicode.cldr.util.XMLSource;

/**
 * Checks locale data for coverage.<br>
 * Options:<br>
 * CheckCoverage.requiredLevel=value to override the required level. values:
 * comprehensive, modern, moderate, basic...<br>
 * Use the option CheckCoverage.skip=true to skip a locale. For console testing,
 * you want to skip the non-language locales, since they don't typically add,
 * just replace. See CheckCLDR for an example.
 * CoverageLevel.localeType=organization to override the organization.
 * 
 * @author davis
 * 
 */
public class CheckCoverage extends FactoryCheckCLDR {
    static final boolean DEBUG = false;
    static final boolean DEBUG_SET = false;
    private static CoverageLevel2 coverageLevel;
    private Level requiredLevel;

    SupplementalDataInfo supplementalData;

    // private boolean requireConfirmed = true;
    // private Matcher specialsToTestMatcher = CLDRFile.specialsToPushFromRoot.matcher("");

    public CheckCoverage(Factory factory) {
        super(factory);
    }

    public CheckCLDR handleCheck(String path, String fullPath, String value,
        Map<String, String> options, List<CheckStatus> result) {

        if (isSkipTest()) return this;

        if (getResolvedCldrFileToCheck().isPathExcludedForSurvey(path)) return this;

        // skip if we are not the winning path
        if (!getResolvedCldrFileToCheck().isWinningPath(path)) {
            return this;
        }

        // if (false && path.indexOf("localeDisplayNames") >= 0 && path.indexOf("\"wo") >= 0) {
        // System.out.println("debug: " + value);
        // }
        //
        // if (path.indexOf("localeDisplayNames") < 0 && path.indexOf("currencies") < 0 && path.indexOf("exemplarCity")
        // < 0) return this;
        //
        // // skip all items that are in anything but raw codes

        String source = getResolvedCldrFileToCheck().getSourceLocaleID(path, null);

        // if the source is a language locale (that is, not root or code fallback) then we have something already, so
        // skip.
        // we test stuff matching specialsToKeep, or code fallback
        // skip anything else
        if (!source.equals(XMLSource.CODE_FALLBACK_ID)
            && !source.equals("root")
            && (path.indexOf("metazone") < 0 || value != null && value.length() > 0)) {
            return this; // skip!
        }

        if (path == null) {
            throw new InternalCldrException("Empty path!");
        } else if (getCldrFileToCheck() == null) {
            throw new InternalCldrException("No file to check!");
        }

        // check to see if the level is good enough
        Level level = coverageLevel.getLevel(path);

        if (level == Level.UNDETERMINED) return this; // continue if we don't know what the status is
        if (requiredLevel.compareTo(level) >= 0) {
            result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.warningType)
                .setSubtype(Subtype.coverageLevel)
                .setCheckOnSubmit(false)
                .setMessage("Needed to meet {0} coverage level.", new Object[] { level }));
        } else if (DEBUG) {
            System.out.println(level + "\t" + path);
        }
        return this;
    }

    public CheckCLDR setCldrFileToCheck(CLDRFile cldrFileToCheck, Map<String, String> options,
        List<CheckStatus> possibleErrors) {
        if (cldrFileToCheck == null) return this;
        setSkipTest(true);
        final String localeID = cldrFileToCheck.getLocaleID();
        if (localeID.equals(new LanguageTagParser().set(localeID).getLanguageScript())) {
            supplementalData = SupplementalDataInfo.getInstance(cldrFileToCheck.getSupplementalDirectory());
            coverageLevel = CoverageLevel2.getInstance(supplementalData, localeID);
            PluralInfo pluralInfo = supplementalData.getPlurals(PluralType.cardinal, localeID);
            if (pluralInfo == supplementalData.getPlurals(PluralType.cardinal, "root")) {
                possibleErrors.add(new CheckStatus()
                    .setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.missingPluralInfo)
                    .setMessage("Missing Plural Information - see supplemental plural charts to file bug.",
                        new Object[] {}));
            }
        }

        if (options != null && options.get("CheckCoverage.skip") != null) return this;
        super.setCldrFileToCheck(cldrFileToCheck, options, possibleErrors);
        if (localeID.equals("root")) return this;

        // Set to minimal if not in data submission
        if (false && Phase.FINAL_TESTING == getPhase()) {
            requiredLevel = Level.POSIX;
        } else {
            requiredLevel = coverageLevel.getRequiredLevel(localeID, options);
        }
        if (DEBUG) {
            System.out.println("requiredLevel: " + requiredLevel);
        }

        setSkipTest(false);
        return this;
    }

    public void setRequiredLevel(Level level) {
        requiredLevel = level;
    }

    public Level getRequiredLevel() {
        return requiredLevel;
    }
}
