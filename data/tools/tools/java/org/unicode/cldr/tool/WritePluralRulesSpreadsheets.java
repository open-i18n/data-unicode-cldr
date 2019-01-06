/*
 *******************************************************************************
 * Copyright (C) 2013, Google Inc. and International Business Machines Corporation and  *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package org.unicode.cldr.tool;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Set;

import org.unicode.cldr.tool.PluralRulesFactory.SamplePatterns;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;

import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.FixedDecimal;
import com.ibm.icu.text.PluralRules.FixedDecimalRange;
import com.ibm.icu.text.PluralRules.FixedDecimalSamples;
import com.ibm.icu.text.PluralRules.SampleType;
import com.ibm.icu.util.ULocale;

/**
 * @author markdavis
 */
public class WritePluralRulesSpreadsheets {
    // This is older code that we might rework in the future...
    public static void main(String[] args) {
        // ranges();
        FindPluralDifferences.diff();
    }

    private static void ranges() {
        SupplementalDataInfo supplemental = SupplementalDataInfo.getInstance();
        Set<String> locales = StandardCodes.make().getLocaleCoverageLocales("Google"); // superset
        //Map<ULocale, PluralRulesFactory.SamplePatterns> sampleMap = PluralRulesFactory.getLocaleToSamplePatterns();
        Factory factory = TestInfo.getInstance().getCldrFactory();

        for (String locale : locales) {
            if (locale.equals("*")) {
                continue;
            }
            String rangePattern = factory.make(locale, true).getStringValue("//ldml/numbers/miscPatterns[@numberSystem=\"latn\"]/pattern[@type=\"range\"]");
            PluralRules rules = supplemental.getPlurals(locale).getPluralRules();
            SamplePatterns samplePatterns = PluralRulesFactory.getSamplePatterns(new ULocale(locale));
            Set<String> keywords = rules.getKeywords();
            //Map<String, FixedDecimal> leastMap = new LinkedHashMap<String, FixedDecimal>();
            //Map<String, FixedDecimal> greatestMap = new LinkedHashMap<String, FixedDecimal>();
            for (String start : keywords) {
                FixedDecimal small = getSample(rules, start, null); // smallest
                String startPattern = getSamplePattern(samplePatterns, start);
                for (String end : keywords) {
                    FixedDecimal large = getSample(rules, end, small); // smallest
                    if (large == null) {
                        continue;
                    }
                    String endPattern = getSamplePattern(samplePatterns, end);
                    String range = MessageFormat.format(rangePattern, small.toString(), large.toString());
                    System.out.println(locale
                        + "\t" + start + "—" + end
                        + "\t" + (startPattern.contains("{0}") ? startPattern.replace("{0}", range) : "?")
                        + "\t" + (endPattern.contains("{0}") ? endPattern.replace("{0}", range) : "?")
                        );
                }
            }
        }

    }

    public static SamplePatterns getSamples(Map<ULocale, PluralRulesFactory.SamplePatterns> sampleMap, String locale) {
        while (true) {
            SamplePatterns result = sampleMap.get(new ULocale(locale));
            if (result != null) {
                return result;
            }
            // just truncate
            int pos = locale.lastIndexOf('_');
            if (pos < 0) {
                return null;
            }
            locale = locale.substring(0, pos);
        }
    }

    public static String getSamplePattern(SamplePatterns samplePatterns, String start) {
        if (samplePatterns != null) {
            Map<Count, String> samples = samplePatterns.keywordToPattern;
            if (samplePatterns != null) {
                String result = samples.get(Count.valueOf(start));
                if (result != null) {
                    return result;
                }
            }
        }
        return "{0} {no pattern available}";
    }

    static FixedDecimal getSample(PluralRules rules, String start, FixedDecimal minimum) {
        FixedDecimal result = getSample(rules, start, SampleType.INTEGER, minimum);
        FixedDecimal result2 = getSample(rules, start, SampleType.DECIMAL, minimum);
        if (result == null) {
            return result2;
        }
        return result;
    }

    static FixedDecimal getSample(PluralRules rules, String start, SampleType sampleType, FixedDecimal minimum) {
        FixedDecimalSamples samples = rules.getDecimalSamples(start, sampleType);
        if (samples == null) {
            return null;
        }
        Set<FixedDecimalRange> samples2 = samples.getSamples();
        if (samples2 == null) {
            return null;
        }
        for (FixedDecimalRange sample : samples2) {
            if (minimum == null) {
                return sample.start;
            } else if (minimum.getSource() < sample.start.getSource()) {
                return sample.start;
            } else if (minimum.getSource() < sample.end.getSource()) {
                return sample.end;
            }
        }
        return null;
    }

    //
    //        // TODO use options
    //        public static final String TARGETDIR = "/Users/markdavis/Google Drive/Backup-2012-10-09/Documents/indigo/Generated/icu/plural-verification/";
    //    
    //        public static void main(String[] args) throws Exception {
    //            if (args.length == 0) {
    //                args = new String[] {"verify"};
    //            }
    //            for (String arg : args) {
    //                if (arg.equalsIgnoreCase("original")) {
    //                    generateSamples(SampleStyle.original);
    //                } else if (arg.startsWith("verify")) {
    //                    showRules();
    //                    generateSamples(SampleStyle.samples);
    //                    generateSamples(SampleStyle.verify);
    //                } else if (arg.equalsIgnoreCase("oldSnap")) {
    //                    generateLOCALE_SNAPSHOT(PluralRulesFactory.NORMAL);
    //                } else if (arg.equalsIgnoreCase("ranges")) {
    //                    generateRanges(PluralRulesFactory.NORMAL);
    //                } else if (arg.equalsIgnoreCase("newSnap")) {
    //                    generateLOCALE_SNAPSHOT(PluralRulesFactory.ALTERNATE);
    //                } else if (arg.equalsIgnoreCase("fromList")) {
    //                    getOriginalSamples();
    //                } else {
    //                    throw new IllegalArgumentException("Bad arg: <" + arg + ">");
    //                }
    //            }
    //        }
    //    
    //        /**
    //         * @param normal
    //         */
    //        private static void generateRanges(PluralRulesFactory normal) {
    //            Map<ULocale, SamplePatterns> patternMap = PluralRulesFactory.getLocaleToSamplePatterns();
    //            NumberFormat enf = NumberFormat.getInstance(ULocale.ENGLISH);
    //    
    //            for (String localeString : FOCUS_LOCALES) {
    //                ULocale locale = new ULocale(localeString);
    //                PluralRules pr = normal.forLocale(locale);
    //                SamplePatterns patterns = patternMap.get(locale);
    //                if (patterns == null) {
    //                    System.out.println("Skipping " + locale);
    //                    continue;
    //                }
    //                System.out.println();
    //                NumberFormat nf = NumberFormat.getInstance(new ULocale(localeString + "@numbers=latn"));
    //                Map<String, FixedDecimal[]> samples = getSampleRanges(pr);
    //                
    //                Set<String> keywords = pr.getKeywords();
    //                for (String keyword1 : keywords) {
    //                    FixedDecimal sample1 = samples.get(keyword1)[0];
    //                    
    //                    for (String keyword2 : keywords) {
    //                        FixedDecimal sample2 = samples.get(keyword2)[1];
    //                        if (sample2.source <= sample1.source) {
    //                            continue;
    //                        }
    //                        // pick the pattern for the second item
    //                        String pattern = patterns.keywordToPattern.get(keyword2);
    //                        if (pattern == null) {
    //                            throw new IllegalArgumentException("Missing pattern for <" + keyword2 + "> for " + locale);
    //                        }
    //                        String formattedRange = format(nf, sample1) + "-" + format(nf, sample2);
    //                        String eformattedRange = format(enf, sample1) + "-" + format(enf, sample2);
    //                        String result = pattern.contains("{0}") ? pattern.replace("{0}", formattedRange)
    //                                : pattern + " " + formattedRange;
    //                        System.out.println(locale + "\t"
    //                                + eformattedRange + " items\t"
    //                                + keyword1 + "\t" + keyword2 + "\t"
    //                                + result 
    //                                );
    //                    }
    //                }
    //            }
    //        }
    //    
    //        private static String format(NumberFormat nf, FixedDecimal sample1) {
    //            nf.setMaximumFractionDigits(sample1.getVisibleDecimalDigitCount());
    //            nf.setMinimumFractionDigits(sample1.getVisibleDecimalDigitCount());
    //            String format = nf.format(sample1.source);
    //            return format;
    //        }
    //    
    //        /**
    //         * @param pr
    //         * @return
    //         */
    //        private static Map<String, FixedDecimal[]> getSampleRanges(PluralRules pr) {
    //            Map<String, FixedDecimal[]> result = new HashMap();
    //            for (int i = 0; i < 1000; ++i) {
    //                String keyword = pr.select(i);
    //                addRangeMaybe(result, i, keyword);
    //            }
    //            for (String keyword : pr.getKeywords()) {
    //                for (Double x : pr.getSamples(keyword)) {
    //                    addRangeMaybe(result, x, keyword);
    //                }
    //            }
    //            return result;
    //        }
    //    
    //        private static void addRangeMaybe(Map<String, FixedDecimal[]> result, double i, String keyword) {
    //            FixedDecimal[] range = result.get(keyword);
    //            if (range == null) {
    //                result.put(keyword, range = new FixedDecimal[2]);
    //                range[0] = new FixedDecimal(Double.MAX_VALUE);
    //                range[1] = new FixedDecimal(Double.MIN_VALUE);
    //            }
    //            if (i < range[0].source) {
    //                range[0] = new FixedDecimal(i);
    //            }
    //            if (i > range[1].source) {
    //                range[1] = new FixedDecimal(i);
    //            }
    //        }
    //    
    //        /**
    //         * @param samples
    //         * @return
    //         */
    //        private static Double getBiggest(Collection<Double> samples) {
    //            Double result = null;
    //            for (Double item : samples) {
    //                if (result == null || result < item) {
    //                    result = item;
    //                }
    //            }
    //            return result;
    //        }
    //    
    //        static final String[] FOCUS_LOCALES = ("af,am,ar,az,bg,bn,ca,cs,cy,da,de,el,en,es,et,eu,fa,fi,fil,fr,gl,gu," +
    //                "hi,hr,hu,hy,id,is,it,he,ja,ka,kk,km,kn,ko,ky,lo,lt,lv,mk,ml,mn,mr,ms,my,ne,nl,nb," +
    //                "pa,pl,pt,ro,ru,si,sk,sl,sq,sr,sv,sw,ta,te,th,tr,uk,ur,uz,vi,zh,zu").split("\\s*,\\s*");
    //    
    //    
    //        static final String[][] ORIGINAL_SAMPLES = {
    //            {"af", "0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"am", "0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"ar", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.003, 0.01, 0.010, 0.011, 0.012, 0.013, 0.02, 0.020, 0.03, 0.1, 0.10, 0.100, 0.101, 0.102, 0.11, 0.12, 0.13, 0.2, 0.20, 0.3, 1.0, 1.00, 1.000, 1.002, 1.003, 1.010, 1.011, 1.012, 1.013, 1.02, 1.020, 1.03, 1.10, 1.100, 1.101, 1.102, 1.11, 1.12, 1.13, 1.2, 1.20, 1.3, 2.0, 2.00, 2.000, 2.001, 2.003, 2.01, 2.010, 2.011, 2.012, 2.013, 2.020, 2.03, 2.1, 2.10, 2.100, 2.101, 2.102, 2.11, 2.12, 2.13, 2.20, 2.3, 3.0, 3.00, 3.000, 3.001, 3.002, 3.01, 3.011, 3.012, 3.013, 3.02, 3.020, 3.1, 3.100, 3.101, 3.102, 3.11, 3.12, 3.13, 3.2, 3.20, 11.0, 11.00, 11.000, 11.001, 11.002, 11.003, 11.01, 11.010, 11.02, 11.03, 11.1, 11.10, 11.100, 11.101, 11.102, 11.2, 11.3, 100.0, 100.00, 100.000, 100.001, 100.002, 100.003, 100.01, 100.010, 100.011, 100.012, 100.013, 100.02, 100.020, 100.03, 100.1, 100.10, 100.11, 100.12, 100.13, 100.2, 100.20, 100.3"},
    //            {"bg", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"br", "0.0, 0.00, 0.000, 0.001, 0.002, 0.003, 0.005, 0.01, 0.010, 0.011, 0.012, 0.013, 0.02, 0.03, 0.05, 0.1, 0.10, 0.11, 0.12, 0.13, 0.2, 0.3, 0.5, 1.0, 1.00, 1.000, 1.002, 1.003, 1.005, 1.010, 1.011, 1.012, 1.013, 1.02, 1.03, 1.05, 1.10, 1.11, 1.12, 1.13, 1.2, 1.3, 1.5, 2, 2.0, 2.00, 2.000, 2.001, 2.003, 2.005, 2.01, 2.010, 2.011, 2.012, 2.013, 2.03, 2.05, 2.1, 2.10, 2.11, 2.12, 2.13, 2.3, 2.5, 3.0, 3.00, 3.000, 3.001, 3.002, 3.005, 3.01, 3.010, 3.011, 3.012, 3.013, 3.02, 3.05, 3.1, 3.10, 3.11, 3.12, 3.13, 3.2, 3.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.003, 5.01, 5.02, 5.03, 5.1, 5.2, 5.3, 1000000.0, 1000000.00, 1000000.000, 1000000.001, 1000000.002, 1000000.003, 1000000.005, 1000000.01, 1000000.010, 1000000.011, 1000000.012, 1000000.013, 1000000.02, 1000000.03, 1000000.05, 1000000.1, 1000000.10, 1000000.11, 1000000.12, 1000000.13, 1000000.2, 1000000.3, 1000000.5"},
    //            {"ca", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"cs", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.005, 0.01, 0.010, 0.011, 0.012, 0.02, 0.05, 0.1, 0.10, 0.11, 0.12, 0.2, 0.5, 1.0, 1.00, 1.000, 1.002, 1.005, 1.010, 1.011, 1.012, 1.02, 1.05, 1.10, 1.11, 1.12, 1.2, 1.5, 2.0, 2.00, 2.000, 2.001, 2.005, 2.01, 2.010, 2.011, 2.012, 2.05, 2.1, 2.10, 2.11, 2.12, 2.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.01, 5.02, 5.1, 5.2"},
    //            {"cy", "0.0, 0.00, 0.000, 0.001, 0.002, 0.003, 0.004, 0.006, 0.01, 0.010, 0.011, 0.012, 0.013, 0.016, 0.02, 0.03, 0.04, 0.06, 0.1, 0.10, 0.11, 0.12, 0.13, 0.16, 0.2, 0.3, 0.4, 0.6, 1.0, 1.00, 1.000, 1.002, 1.003, 1.004, 1.006, 1.010, 1.011, 1.012, 1.013, 1.016, 1.02, 1.03, 1.04, 1.06, 1.10, 1.11, 1.12, 1.13, 1.16, 1.2, 1.3, 1.4, 1.6, 2.0, 2.00, 2.000, 2.001, 2.003, 2.004, 2.006, 2.01, 2.010, 2.011, 2.012, 2.013, 2.016, 2.03, 2.04, 2.06, 2.1, 2.10, 2.11, 2.12, 2.13, 2.16, 2.3, 2.4, 2.6, 3.0, 3.00, 3.000, 3.001, 3.002, 3.004, 3.006, 3.01, 3.010, 3.011, 3.012, 3.013, 3.016, 3.02, 3.04, 3.06, 3.1, 3.10, 3.11, 3.12, 3.13, 3.16, 3.2, 3.4, 3.6, 4.0, 4.00, 4.000, 4.001, 4.002, 4.003, 4.006, 4.01, 4.02, 4.03, 4.06, 4.1, 4.2, 4.3, 4.6, 6.0, 6.00, 6.000, 6.001, 6.002, 6.003, 6.004, 6.01, 6.010, 6.011, 6.012, 6.013, 6.016, 6.02, 6.03, 6.04, 6.1, 6.10, 6.11, 6.12, 6.13, 6.16, 6.2, 6.3, 6.4, 8"},
    //            {"da", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"de", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"dz", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"el", "2, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"es", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"et", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"eu", "0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1, 4"},
    //            {"fa", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"fi", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"fil", "0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 0.5, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"fr", "0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"gl", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"gu", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"he", "0.0, 0.00, 0.000, 0.001, 0.002, 0.003, 0.01, 0.010, 0.011, 0.012, 0.02, 0.03, 0.1, 0.10, 0.11, 0.12, 0.2, 0.3, 1.0, 1.00, 1.000, 1.002, 1.003, 1.010, 1.011, 1.012, 1.02, 1.03, 1.10, 1.11, 1.12, 1.2, 1.3, 2.0, 2.00, 2.000, 2.001, 2.003, 2.01, 2.010, 2.011, 2.012, 2.03, 2.1, 2.10, 2.11, 2.12, 2.3, 3.0, 3.00, 3.000, 3.001, 3.002, 3.01, 3.010, 3.02, 3.1, 3.10, 3.2, 10.0, 10.00, 10.000, 10.001, 10.002, 10.003, 10.01, 10.011, 10.012, 10.02, 10.03, 10.1, 10.11, 10.12, 10.2, 10.3"},
    //            {"hi", "0.0, 0.00, 1.0, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.0, 1.0, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"hr", "0.0, 0.00, 0.000, 0.001, 0.002, 0.005, 0.01, 0.010, 0.011, 0.012, 0.02, 0.05, 0.1, 0.10, 0.11, 0.12, 0.2, 0.5, 0.5, 1.0, 1.00, 1.000, 1.002, 1.005, 1.010, 1.011, 1.012, 1.02, 1.05, 1.10, 1.11, 1.12, 1.2, 1.5, 2.0, 2.00, 2.000, 2.001, 2.005, 2.01, 2.010, 2.011, 2.012, 2.05, 2.1, 2.10, 2.11, 2.12, 2.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.01, 5.02, 5.1, 5.2"},
    //            {"hu", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"hy", "0, 0, 0, 0, 0.001, 0.002, 0.003, 0.01, 0.01, 0.011, 0.012, 0.013, 0.02, 0.02, 0.03, 0.1, 0.1, 0.1, 0.101, 0.102, 0.11, 0.12, 0.13, 0.2, 0.2, 0.3, 1, 1, 1, 1.002, 1.003, 1.01, 1.011, 1.012, 1.013, 1.02, 1.02, 1.03, 1.1, 1.1, 1.101, 1.102, 1.11, 1.12, 1.13, 1.2, 1.2, 1.3, 2, 2, 2, 2.001, 2.003, 2.01, 2.01, 2.011, 2.012, 2.013, 2.02, 2.03, 2.1, 2.1, 2.1, 2.101, 2.102, 2.11, 2.12, 2.13, 2.2, 2.3, 3, 3, 3, 3.001, 3.002, 3.01, 3.011, 3.012, 3.013, 3.02, 3.02, 3.1, 3.1, 3.101, 3.102, 3.11, 3.12, 3.13, 3.2, 3.2, 11, 11, 11, 11.001, 11.002, 11.003, 11.01, 11.01, 11.02, 11.03, 11.1, 11.1, 11.1, 11.101, 11.102, 11.2, 11.3, 100, 100, 100, 100.001, 100.002, 100.003, 100.01, 100.01, 100.011, 100.012, 100.013, 100.02, 100.02, 100.03, 100.1, 100.1, 100.11, 100.12, 100.13, 100.2, 100.2, 100.3"},
    //            {"id", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"is", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"it", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"ja", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"km", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"kn", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"ko", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"lo", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"lt", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.012, 0.02, 0.1, 0.10, 0.11, 0.12, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.012, 1.02, 1.10, 1.11, 1.12, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.010, 2.011, 2.012, 2.1, 2.10, 2.11, 2.12, 10.0, 10.00, 10.000, 10.001, 10.002, 10.01, 10.02, 10.1, 10.2"},
    //            {"lv", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"ml", "0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"mr", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"ms", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"nb", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"ne", "0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1, 2, 1, 0, 2, 1, 0"},
    //            {"nl", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"pl", "0.0, 0.00, 0.000, 0.001, 0.002, 0.005, 0.01, 0.010, 0.011, 0.012, 0.02, 0.05, 0.1, 0.10, 0.11, 0.12, 0.2, 0.5, 0.5, 1.0, 1.00, 1.000, 1.002, 1.005, 1.010, 1.011, 1.012, 1.02, 1.05, 1.10, 1.11, 1.12, 1.2, 1.5, 2.0, 2.00, 2.000, 2.001, 2.005, 2.01, 2.010, 2.011, 2.012, 2.05, 2.1, 2.10, 2.11, 2.12, 2.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.01, 5.02, 5.1, 5.2"},
    //            {"pt", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"pt_PT", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"ro", "0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.020, 0.021, 0.022, 0.1, 0.10, 0.11, 0.2, 0.20, 0.21, 0.22, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.020, 1.021, 1.022, 1.10, 1.11, 1.2, 1.20, 1.21, 1.22, 2.0, 2.00, 2.000, 2.001, 2.01, 2.020, 2.021, 2.022, 2.1, 2.20, 2.21, 2.22, 20, 20.0, 20.00, 20.000, 20.001, 20.002, 20.01, 20.010, 20.011, 20.02, 20.1, 20.10, 20.11, 20.2"},
    //            {"ru", "0.0, 0.00, 0.000, 0.001, 0.002, 0.005, 0.01, 0.010, 0.011, 0.012, 0.02, 0.05, 0.1, 0.10, 0.11, 0.12, 0.2, 0.5, 0.5, 1.0, 1.00, 1.000, 1.002, 1.005, 1.010, 1.011, 1.012, 1.02, 1.05, 1.10, 1.11, 1.12, 1.2, 1.5, 2.0, 2.00, 2.000, 2.001, 2.005, 2.01, 2.010, 2.011, 2.012, 2.05, 2.1, 2.10, 2.11, 2.12, 2.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.01, 5.02, 5.1, 5.2"},
    //            {"si", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"sk", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.005, 0.01, 0.010, 0.011, 0.012, 0.02, 0.05, 0.1, 0.10, 0.11, 0.12, 0.2, 0.5, 1.0, 1.00, 1.000, 1.002, 1.005, 1.010, 1.011, 1.012, 1.02, 1.05, 1.10, 1.11, 1.12, 1.2, 1.5, 2.0, 2.00, 2.000, 2.001, 2.005, 2.01, 2.010, 2.011, 2.012, 2.05, 2.1, 2.10, 2.11, 2.12, 2.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.01, 5.02, 5.1, 5.2"},
    //            {"sl", "0.0, 0.00, 0.000, 0.001, 0.002, 0.003, 0.005, 0.01, 0.010, 0.011, 0.012, 0.013, 0.02, 0.03, 0.05, 0.1, 0.10, 0.11, 0.12, 0.13, 0.2, 0.3, 0.5, 1.0, 1.00, 1.000, 1.002, 1.003, 1.005, 1.010, 1.011, 1.012, 1.013, 1.02, 1.03, 1.05, 1.10, 1.11, 1.12, 1.13, 1.2, 1.3, 1.5, 2, 2.0, 2.00, 2.000, 2.001, 2.003, 2.005, 2.01, 2.010, 2.011, 2.012, 2.013, 2.03, 2.05, 2.1, 2.10, 2.11, 2.12, 2.13, 2.3, 2.5, 3.0, 3.00, 3.000, 3.001, 3.002, 3.005, 3.01, 3.010, 3.011, 3.012, 3.013, 3.02, 3.05, 3.1, 3.10, 3.11, 3.12, 3.13, 3.2, 3.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.003, 5.01, 5.02, 5.03, 5.1, 5.2, 5.3"},
    //            {"sr", "0.0, 0.00, 0.000, 0.001, 0.002, 0.005, 0.01, 0.010, 0.011, 0.012, 0.02, 0.05, 0.1, 0.10, 0.11, 0.12, 0.2, 0.5, 0.5, 1.0, 1.00, 1.000, 1.002, 1.005, 1.010, 1.011, 1.012, 1.02, 1.05, 1.10, 1.11, 1.12, 1.2, 1.5, 2.0, 2.00, 2.000, 2.001, 2.005, 2.01, 2.010, 2.011, 2.012, 2.05, 2.1, 2.10, 2.11, 2.12, 2.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.01, 5.02, 5.1, 5.2"},
    //            {"sv", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"sw", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"ta", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"te", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"th", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"tr", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"uk", "0.0, 0.00, 0.000, 0.001, 0.002, 0.005, 0.01, 0.010, 0.011, 0.012, 0.02, 0.05, 0.1, 0.10, 0.11, 0.12, 0.2, 0.5, 0.5, 1.0, 1.00, 1.000, 1.002, 1.005, 1.010, 1.011, 1.012, 1.02, 1.05, 1.10, 1.11, 1.12, 1.2, 1.5, 2.0, 2.00, 2.000, 2.001, 2.005, 2.01, 2.010, 2.011, 2.012, 2.05, 2.1, 2.10, 2.11, 2.12, 2.5, 5.0, 5.00, 5.000, 5.001, 5.002, 5.01, 5.02, 5.1, 5.2"},
    //            {"ur", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1"},
    //            {"vi", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"zh", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"zh_Hant", "0, 0.0, 0.00, 0.000, 0.001, 0.01, 0.010, 0.1, 0.10, 1.0, 1.00, 1.000"},
    //            {"zu", "0, 0.0, 0.00, 0.000, 0.001, 0.002, 0.01, 0.010, 0.011, 0.02, 0.1, 0.10, 0.11, 0.2, 1, 1.0, 1.00, 1.000, 1.002, 1.010, 1.011, 1.02, 1.10, 1.11, 1.2, 2, 2.0, 2.00, 2.000, 2.001, 2.01, 2.1, 10"},
    //        };
    //        static final Map<ULocale,List<FixedDecimal>> LOCALE_TO_ORIGINALS = new HashMap();
    //        static {
    //            for (String[] pair : ORIGINAL_SAMPLES) {
    //                ArrayList<FixedDecimal> row = new ArrayList();
    //                for (String s : pair[1].split("\\s*,\\s*")) {
    //                    row.add(new FixedDecimal(s));
    //                }
    //                LOCALE_TO_ORIGINALS.put(new ULocale(pair[0]), row);
    //            }
    //        }
    //    
    //        public static void getOriginalSamples() {
    //            try {
    //                File file = new File("/Users/markdavis/workspace/icu4j/main/tests/core/src/com/ibm/icu/dev/test/format/plurals.txt");
    //                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    //                Map<String,String> localeToSamples = new TreeMap();
    //                Matcher m = Pattern.compile("\\d+([.]\\d+)?").matcher("");
    //                int count = 0;
    //                while (true) {
    //                    String line = br.readLine();
    //                    ++count;
    //                    if (line == null) break;
    //                    line = line.trim();
    //                    if (line.startsWith("#")) {
    //                        continue;
    //                    }
    //                    String[] parts = line.split("\t");
    //                    try {
    //                        String locale = parts[0];
    //                        String pattern = parts[1];
    //                        boolean found = m.reset(pattern).find();
    //                        if (parts.length != 2 || !found) {
    //                            throw new ArrayIndexOutOfBoundsException();
    //                        }
    //                        String sample = found 
    //                                ? m.group() 
    //                                        : "-1";
    //                                String samples = localeToSamples.get(locale);
    //                                localeToSamples.put(locale, samples == null ? sample : samples + ", " + sample);
    //                    } catch (Exception e) {
    //                        throw new IllegalArgumentException(count + " Line <" + line + ">", e);
    //                    }
    //                }
    //                br.close();
    //                for (Entry<String, String> entry : localeToSamples.entrySet()) {
    //                    System.out.println("{\"" + entry.getKey() + "\", \"" + entry.getValue() + "\"},");
    //                }
    //            } catch (IOException e) {
    //                throw new RuntimeException(e);
    //            }
    //        }
    //    
    //        public static void generateLOCALE_SNAPSHOT(PluralRulesFactory pluralRulesFactory) {
    //            StringBuilder builder = new StringBuilder();
    //            Map<Set<SupplementalDataInfo.PluralInfo.Count>, Relation<String, ULocale>> keywordsToData = new TreeMap();
    //            for (ULocale locale : pluralRulesFactory.getAvailableULocales()) {
    //                builder.setLength(0);
    //                PluralRules rules = pluralRulesFactory.forLocale(locale);
    //                boolean firstKeyword = true;
    //                EnumSet<SupplementalDataInfo.PluralInfo.Count> keywords = CldrUtility.toEnumSet(SupplementalDataInfo.PluralInfo.Count.class, rules.getKeywords());
    //                Relation<String, ULocale> samplesToLocales = keywordsToData.get(keywords);
    //                if (samplesToLocales == null) {
    //                    keywordsToData.put(keywords, samplesToLocales = Relation.of(
    //                            new LinkedHashMap<String,Set<ULocale>>(), LinkedHashSet.class));
    //                }
    //                //System.out.println(locale);
    //                for (SupplementalDataInfo.PluralInfo.Count keyword : keywords) {
    //                    if (firstKeyword) {
    //                        firstKeyword = false;
    //                    } else {
    //                        builder.append(";\t");
    //                    }
    //                    Collection<FixedDecimal> samples = rules.getFractionSamples(keyword.toString());
    //                    if (samples.size() == 0) {
    //                        throw new IllegalArgumentException();
    //                    }
    //                    builder.append(keyword).append(": ");
    //                    boolean first = true;
    //                    for (FixedDecimal n : samples) {
    //                        if (first) {
    //                            first = false;
    //                        } else {
    //                            builder.append(", ");
    //                        }
    //                        builder.append(n);
    //                        //                    for (double j : samples) {
    //                        //                        double sample = i + j/100;
    //                        //                    }
    //                    }
    //                }
    //                samplesToLocales.put(builder.toString(), locale);
    //            }
    //            System.out.println("    static final String[] LOCALE_SNAPSHOT = {");
    //            for (Entry<Set<SupplementalDataInfo.PluralInfo.Count>, Relation<String, ULocale>> keywordsAndData : keywordsToData.entrySet()) {
    //                System.out.println("\n        // " + keywordsAndData.getKey());
    //                for (Entry<String, Set<ULocale>> samplesAndLocales : keywordsAndData.getValue().keyValuesSet()) {
    //                    Set<ULocale> locales = samplesAndLocales.getValue();
    //                    // check functional equivalence
    //                    boolean[] isAvailable = new boolean[1];
    //                    for (ULocale locale : locales) {
    //                        ULocale base = pluralRulesFactory.getFunctionalEquivalent(locale, isAvailable);
    //                        if (!locales.contains(base) && base.toString().length() != 0) {
    //                            System.out.println("**" + locales + " doesn't contain " + base);
    //                        }
    //                    }
    //    
    //                    System.out.println(
    //                            "        \"" + CollectionUtilities.join(locales, ",")
    //                            + ";\t" + samplesAndLocales.getKey() + "\",");
    //                }
    //            }
    //            System.out.println("    };");
    //        }
    //    
    //        private static class OldNewData extends Row.R5<String, String, String, String, String> {
    //            public OldNewData(String oldRules, String oldSamples, String newRules, String newSamples, String intDiff) {
    //                super(oldRules, oldSamples, newRules, newSamples, intDiff);
    //            }
    //        }
    //    
    //        public static void showRules() throws IOException {
    //            BufferedWriter writer = getWriter("all-" + SampleStyle.rules + ".tsv");
    //    
    //            if (true) {
    //                // for debugging
    //                PluralRules rules = PluralRulesFactory.ALTERNATE.forLocale(new ULocale("lv"));
    //                rules.select(2.0d, 2, 0);
    //            }
    //            // System.out.println(new TreeSet(Arrays.asList(locales)));
    //            Relation<Map<String,OldNewData>, String> rulesToLocale = Relation.of(
    //                    new TreeMap<Map<String,OldNewData>, Set<String>>(
    //                            new CollectionUtilities.MapComparator<String,OldNewData>()), TreeSet.class);
    //            for (String localeString : FOCUS_LOCALES) {
    //                ULocale locale = new ULocale(localeString);
    //                PluralRules oldRules = PluralRulesFactory.NORMAL.forLocale(locale);
    //                PluralRules newRules = PluralRulesFactory.ALTERNATE.hasOverride(locale) ? PluralRulesFactory.ALTERNATE.forLocale(locale) : null;
    //                Set<String> keywords = oldRules.getKeywords();
    //                if (newRules != null) {
    //                    TreeSet<String> temp = new TreeSet<String>(PluralRules.KEYWORD_COMPARATOR);
    //                    temp.addAll(keywords);
    //                    temp.addAll(newRules.getKeywords());
    //                    keywords = temp;
    //                }
    //                Map<String,OldNewData> temp = new LinkedHashMap();
    //                for (String keyword : keywords) {
    //                    Collection<FixedDecimal> oldFractionSamples = oldRules.getFractionSamples(keyword);
    //                    Collection<FixedDecimal> newFractionSamples = newRules == null ? null : newRules.getFractionSamples(keyword);
    //    
    //                    // add extra samples if we have some, or if the rules differ
    //    
    //                    if (newRules != null) {
    //                        oldFractionSamples = oldFractionSamples == null ? new TreeSet()
    //                        : new TreeSet(oldFractionSamples);
    //                        newFractionSamples = newFractionSamples == null ? new TreeSet()
    //                        : new TreeSet(newFractionSamples);
    //                        //                    if (extraSamples != null) {
    //                        //                        for (NumberPlus sample : extraSamples) {
    //                        //                            if (oldRules.select(sample.source, sample.getVisibleDecimalDigitCount(), sample.fractionalDigits).equals(keyword)) {
    //                        //                                oldFractionSamples.add(sample);
    //                        //                            }
    //                        //                            if (newRules != null && newRules.select(sample.source, sample.getVisibleDecimalDigitCount(), sample.fractionalDigits).equals(keyword)) {
    //                        //                                newFractionSamples.add(sample);
    //                        //                            }
    //                        //                        }
    //                        //                    }
    //    
    //                        // if the rules differ, then add samples from each to the other
    //                        if (newRules != null) {
    //                            for (FixedDecimal sample : oldRules.getFractionSamples()) {
    //                                if (newRules.select(sample).equals(keyword)) {
    //                                    newFractionSamples.add(sample);
    //                                }
    //                            }
    //                            for (FixedDecimal sample : newRules.getFractionSamples()) {
    //                                if (oldRules.select(sample).equals(keyword)) {
    //                                    oldFractionSamples.add(sample);
    //                                }
    //                            }
    //                        }
    //                    }
    //    
    //                    String intDiff = newRules == null ? "" : getDiffs(oldFractionSamples, newFractionSamples);
    //    
    //                    String oldRulesString = rulesForDisplay(oldRules, keyword);
    //                    if (oldRulesString == null) {
    //                        oldRulesString = "";
    //                    }
    //                    String newRulesString = newRules == null ? "" : rulesForDisplay(newRules, keyword);
    //                    if (newRulesString == null) {
    //                        newRulesString = "";
    //                    }
    //                    if (oldRulesString.length() == 0 && newRulesString.length() != 0) {
    //                        oldRulesString = "<NEW SPLITS>";
    //                    } else if (oldRulesString.length() != 0 && newRulesString.length() == 0 && newRules != null) {
    //                        newRulesString = "<NEW MERGES>";
    //                    }
    //                    temp.put(keyword, new OldNewData(
    //                            oldRulesString, 
    //                            oldFractionSamples == null ? "" : "'" + CollectionUtilities.join(oldFractionSamples, ", "),
    //                                    newRulesString, 
    //                                    newFractionSamples == null ? "" : "'" + CollectionUtilities.join(newFractionSamples, ", "),
    //                                            intDiff.length() == 0 ? "" : "'" + intDiff
    //                            ));
    //                }
    //                rulesToLocale.put(temp, locale.toString());
    //            }
    //            writer.write("Locales\tPC\tOld Rules\tOld Sample Numbers\tNew Rules\tNew Sample Numbers\tInt-Diff\n");
    //            for (Entry<Map<String, OldNewData>, Set<String>> entry : rulesToLocale.keyValuesSet()) {
    //                String localeList = CollectionUtilities.join(entry.getValue(), " ");
    //                for (Entry<String, OldNewData> keywordRulesSamples : entry.getKey().entrySet()) {
    //                    writer.write(
    //                            localeList // locale
    //                            + "\t" + keywordRulesSamples.getKey() // keyword
    //                            + "\t" + keywordRulesSamples.getValue().get0() // rules
    //                            + "\t" + keywordRulesSamples.getValue().get1() // samples
    //                            + "\t" + keywordRulesSamples.getValue().get2() // rules
    //                            + "\t" + keywordRulesSamples.getValue().get3() // samples
    //                            + "\t" + keywordRulesSamples.getValue().get4() // int diff
    //                            + "\n"
    //                            );
    //                    localeList = "";
    //                }
    //            }
    //    
    //            if (false) {
    //                System.out.println("\n\nOld Rules for Locales");
    //                for (String localeString : FOCUS_LOCALES) {
    //                    ULocale locale = new ULocale(localeString);
    //                    PluralRules oldRules = PluralRules.forLocale(locale);
    //                    System.out.println("{\"" + locale.toString() + "\", \"" + oldRules.toString() + "\"},");
    //                }
    //            }
    //            writer.close();
    //        }
    //    
    //        /**
    //         * @param oldFractionSamples
    //         * @param newFractionSamples
    //         * @return
    //         */
    //        private static String getDiffs(Collection<FixedDecimal> oldFractionSamples, 
    //                Collection<FixedDecimal> newFractionSamples) {
    //            oldFractionSamples = oldFractionSamples == null ? Collections.EMPTY_SET : oldFractionSamples;
    //            newFractionSamples = newFractionSamples == null ? Collections.EMPTY_SET : newFractionSamples;
    //    
    //            TreeSet<FixedDecimal> additions = new TreeSet(newFractionSamples);
    //            additions.removeAll(oldFractionSamples);
    //            TreeSet<FixedDecimal> removals = new TreeSet(oldFractionSamples);
    //            removals.removeAll(newFractionSamples);
    //            StringBuffer result = new StringBuffer();
    //            addInts(additions, "+", result);
    //            addInts(removals, "-", result);
    //            return result.toString();
    //        }
    //    
    //        private static void addInts(TreeSet<FixedDecimal> additions, String title, StringBuffer result) {
    //            for (FixedDecimal n : additions) {
    //                if (n.getVisibleDecimalDigitCount() == 0) {
    //                    if (result.length() != 0) {
    //                        result.append("; ");
    //                    }
    //                    result.append(title).append(n);
    //                }
    //            }
    //        }
    //    
    //        static final Set<String> NEW_LOCALES = new HashSet(Arrays.asList("az,ka,kk,ky,mk,mn,my,pa,sq,uz".split("\\s*,\\s*")));
    //    
    //    
    //        enum SampleStyle {original, samples, rules, verify}
    //    
    //        static void generateSamples(SampleStyle sampleStyle) throws IOException {
    //            LinkedHashSet<ULocale> skippedLocales = new LinkedHashSet<ULocale>();
    //            BufferedWriter writer = null;
    //            if (sampleStyle != SampleStyle.verify) {
    //                writer = getWriter("all-" + sampleStyle + ".tsv");
    //                //writer.write("Plural Category\tEnglish Number\tFormatted Sample\tAcceptable?\tReplacement\n");
    //                writer.write("Locale\tPC\tPattern\tSamples\tRules\tErrors (" + sampleStyle + ")\n");
    //            }
    //    
    //            for (String localeString : FOCUS_LOCALES) {
    //                ULocale locale = new ULocale(localeString);
    //                if (sampleStyle == SampleStyle.verify) {
    //                    writer = getWriter("fraction-" + locale + ".tsv");
    //                    writer.write("Plural Category\tEnglish Number\tFormatted Sample\tAcceptable?\tReplacement\n");
    //                }
    //    
    //                NumberFormat nf = NumberFormat.getInstance(new ULocale(locale.toString()+"@numbers=latn"));
    //                PluralRules newRules = PluralRulesFactory.ALTERNATE.forLocale(locale);
    //                SamplePatterns samplePatterns = PluralRulesFactory.getLocaleToSamplePatterns().get(locale);
    //                if (samplePatterns == null) {
    //                    samplePatterns = PluralRulesFactory.getLocaleToSamplePatterns().get(new ULocale("und"));
    //                }
    //    
    //                //            if (samplePatterns == null && NEW_LOCALES.contains(localeString)) {
    //                //                skippedLocales.add(locale);
    //                //                continue;
    //                //            }
    //                // check for errors. 
    //                samplePatterns.checkErrors(newRules.getKeywords());
    //                // now print.
    //                for (String keyword : newRules.getKeywords()) {
    //                    if (sampleStyle != SampleStyle.samples) {
    //                        Collection<FixedDecimal> samples = getSamples(newRules, keyword, locale, sampleStyle);
    //                        for (FixedDecimal sample : samples) {
    //                            String pattern = samplePatterns.keywordToPattern.get(keyword);
    //                            String str = format(pattern, nf, sample);
    //                            if (sampleStyle == SampleStyle.verify) {
    //                                writer.write(keyword + "\t'" + sample + "\t" + str + "\n");
    //                            } else {
    //                                writer.write(locale + "\t" + keyword + "\t" + sample + "\t" + str + "\n");
    //                            }
    //                        }
    //                        continue;
    //                    }
    //                    String pattern = null;
    //                    String error = null;
    //                    Collection<FixedDecimal> samples = getSamples(newRules, keyword, locale, sampleStyle);
    //                    FixedDecimal first = samples.iterator().next();
    //                    String sample = "??? " + first.toString();
    //                    String rule = "";
    //                    if (samplePatterns == null) {
    //                        pattern = "???";
    //                        error = "\tERROR: Locale data missing";
    //                    } else {
    //                        pattern = samplePatterns.keywordToPattern.get(keyword);
    //                        rule = rulesForDisplay(newRules, keyword);
    //                        error = samplePatterns.keywordToErrors.get(keyword);
    //                        if (pattern == null) {
    //                            pattern = "???";
    //                            error = "\tERROR: Needed for new rules";
    //                        } else {
    //                            StringBuilder buffer = new StringBuilder();
    //                            for (FixedDecimal x : samples) {
    //                                if (buffer.length() != 0) {
    //                                    buffer.append(";  ");
    //                                }
    //                                String str = format(pattern, nf, x);
    //                                buffer.append(str);
    //                            }
    //                            sample = buffer.toString();
    //                        }
    //                    }
    //                    writer.write(locale + "\t" + keyword
    //                            + "\t" + pattern
    //                            + "\t" + sample
    //                            + "\t" + rule
    //                            + error
    //                            + "\n"
    //                            );
    //                }
    //                if (sampleStyle == SampleStyle.verify) {
    //                    writer.close();
    //                }
    //            }
    //            if (sampleStyle != SampleStyle.verify) {
    //                if (skippedLocales.size() != 0) {
    //                    writer.write("SKIP:\t\t\t" + skippedLocales + "\n");
    //                }
    //                writer.close();
    //            }
    //        }
    //    
    //        private static BufferedWriter getWriter(String filename) {
    //            try {
    //                BufferedWriter writer;
    //                String fileName = TARGETDIR + filename;
    //                System.out.println(new File(fileName).getCanonicalPath());
    //                writer = new BufferedWriter(
    //                        new OutputStreamWriter(
    //                                new FileOutputStream(fileName), Charset.forName("UTF-8")));
    //                return writer;
    //            } catch (FileNotFoundException e) {
    //                throw new IllegalArgumentException(e);
    //            } catch (IOException e) {
    //                throw new IllegalArgumentException(e);
    //            }
    //        }
    //    
    //        private static Collection<FixedDecimal> getSamples(PluralRules newRules, String keyword, ULocale locale, SampleStyle sampleStyle) {
    //            Collection<FixedDecimal> result = new TreeSet();
    //            Collection<FixedDecimal> extras;
    //            if (sampleStyle == SampleStyle.original) {
    //                extras = LOCALE_TO_ORIGINALS.get(locale);
    //                if (extras != null) {
    //                    for (FixedDecimal s : extras) {
    //                        if (keyword.equals(newRules.select(s))) {
    //                            result.add(s);
    //                        }
    //                    }
    //                }
    //            }
    //            extras = PluralRulesFactory.getExtraSamples().get(locale);
    //            if (extras == null) {
    //                extras = PluralRulesFactory.getExtraSamples().get(new ULocale("und"));
    //            }
    //            if (extras != null) {
    //                for (FixedDecimal s : extras) {
    //                    if (keyword.equals(newRules.select(s))) {
    //                        result.add(s);
    //                    }
    //                }
    //            }
    //            if (result.size() == 0) {
    //                result = newRules.getFractionSamples(keyword);
    //                if (result.size() == 0) {
    //                    throw new IllegalArgumentException("Internal problem, no samples for " + locale + ":" + keyword);
    //                }
    //                return result;
    //            }
    //            result.addAll(newRules..getgetFractionSamples(keyword));
    //            return result;
    //        }
    //    
    //        private static String rulesForDisplay(PluralRules newRules, String keyword) {
    //            String rule;
    //            rule = newRules.getRules(keyword);
    //            rule = rule != null ? rule.replace(" ", "\u00A0").replace("\u00A0or", " or")
    //                    : keyword.equals("other") ? "<all else>" 
    //                            : "";
    //            return rule;
    //        }
    //    
    //        private static String format(String pattern, NumberFormat nf, FixedDecimal x) {
    //            nf.setMaximumFractionDigits(x.getVisibleDecimalDigitCount());
    //            nf.setMinimumFractionDigits(x.getVisibleDecimalDigitCount());
    //            String str = nf.format(x.source);
    //            return pattern.replace("{0}", str);
    //        }
    //    
    //        /**
    //         * 
    //         */
    //        private static void generateVerificationSamples() {
    //            // TODO Auto-generated method stub
    //    
    //        }
    //    

}
