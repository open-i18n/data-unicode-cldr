package org.unicode.cldr.util;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.test.CoverageLevel2;
import org.unicode.cldr.util.Builder.CBuilder;
import org.unicode.cldr.util.CldrUtility.VariableReplacer;
import org.unicode.cldr.util.DayPeriodInfo.DayPeriod;
import org.unicode.cldr.util.SupplementalDataInfo.BasicLanguageData.Type;
import org.unicode.cldr.util.SupplementalDataInfo.NumberingSystemInfo.NumberingSystemType;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;

import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.impl.IterableComparator;
import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.impl.Row.R4;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Freezable;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

/**
 * Singleton class to provide API access to supplemental data -- in all the supplemental data files.
 * <p>
 * To create, use SupplementalDataInfo.getInstance
 * <p>
 * To add API for new structure, you will generally:
 * <ul>
 * <li>add a Map or Relation as a data member,
 * <li>put a check and handler in MyHandler for the paths that you consume,
 * <li>make the data member immutable in makeStuffSave, and
 * <li>add a getter for the data member
 * </ul>
 * 
 * @author markdavis
 */

public class SupplementalDataInfo {
    private static final boolean DEBUG = false;

    // TODO add structure for items shown by TestSupplementalData to be missing
    /*
     * [calendarData/calendar,
     * characters/character-fallback,
     * measurementData/measurementSystem, measurementData/paperSize,
     * metadata/attributeOrder, metadata/blocking, metadata/deprecated,
     * metadata/distinguishing, metadata/elementOrder, metadata/serialElements, metadata/skipDefaultLocale,
     * metadata/suppress, metadata/validity, metazoneInfo/timezone,
     * timezoneData/mapTimezones,
     * weekData/firstDay, weekData/minDays, weekData/weekendEnd, weekData/weekendStart]
     */
    // TODO: verify that we get everything by writing the files solely from the API, and verifying identity.

    /**
     * Official status of languages
     */
    public enum OfficialStatus {
        unknown("U", 1),
        recognized("R", 1),
        official_minority("OM", 2),
        official_regional("OR", 3),
        de_facto_official("OD", 10),
        official("O", 10);

        private final String shortName;
        private final int weight;

        private OfficialStatus(String shortName, int weight) {
            this.shortName = shortName;
            this.weight = weight;
        }

        public String toShortString() {
            return shortName;
        }

        public int getWeight() {
            return weight;
        }

        public boolean isMajor() {
            return compareTo(OfficialStatus.de_facto_official) >= 0;
        }

        public boolean isOfficial() {
            return compareTo(OfficialStatus.official_regional) >= 0;
        }
    };

    /**
     * Population data for different languages.
     */
    public static final class PopulationData implements Freezable<PopulationData> {
        private double population = Double.NaN;

        private double literatePopulation = Double.NaN;

        private double gdp = Double.NaN;

        private OfficialStatus officialStatus = OfficialStatus.unknown;

        public double getGdp() {
            return gdp;
        }

        public double getLiteratePopulation() {
            return literatePopulation;
        }

        public double getPopulation() {
            return population;
        }

        public PopulationData setGdp(double gdp) {
            if (frozen) {
                throw new UnsupportedOperationException(
                    "Attempt to modify frozen object");
            }
            this.gdp = gdp;
            return this;
        }

        public PopulationData setLiteratePopulation(double literatePopulation) {
            if (frozen) {
                throw new UnsupportedOperationException(
                    "Attempt to modify frozen object");
            }
            this.literatePopulation = literatePopulation;
            return this;
        }

        public PopulationData setPopulation(double population) {
            if (frozen) {
                throw new UnsupportedOperationException(
                    "Attempt to modify frozen object");
            }
            this.population = population;
            return this;
        }

        public PopulationData set(PopulationData other) {
            if (frozen) {
                throw new UnsupportedOperationException(
                    "Attempt to modify frozen object");
            }
            if (other == null) {
                population = literatePopulation = gdp = Double.NaN;
            } else {
                population = other.population;
                literatePopulation = other.literatePopulation;
                gdp = other.gdp;
            }
            return this;
        }

        public void add(PopulationData other) {
            if (frozen) {
                throw new UnsupportedOperationException(
                    "Attempt to modify frozen object");
            }
            population += other.population;
            literatePopulation += other.literatePopulation;
            gdp += other.gdp;
        }

        public String toString() {
            return MessageFormat
                .format(
                    "[pop: {0,number,#,##0},\t lit: {1,number,#,##0.00},\t gdp: {2,number,#,##0},\t status: {3}]",
                    new Object[] { population, literatePopulation, gdp, officialStatus });
        }

        private boolean frozen;

        public boolean isFrozen() {
            return frozen;
        }

        public PopulationData freeze() {
            frozen = true;
            return this;
        }

        public PopulationData cloneAsThawed() {
            throw new UnsupportedOperationException("not yet implemented");
        }

        public OfficialStatus getOfficialStatus() {
            return officialStatus;
        }

        public PopulationData setOfficialStatus(OfficialStatus officialStatus) {
            this.officialStatus = officialStatus;
            return this;
        }
    }

    static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Simple language/script/region information
     */
    public static class BasicLanguageData implements Comparable<BasicLanguageData>,
        com.ibm.icu.util.Freezable<BasicLanguageData> {
        public enum Type {
            primary, secondary
        };

        private Type type = Type.primary;

        private Set<String> scripts = Collections.emptySet();

        private Set<String> territories = Collections.emptySet();

        public Type getType() {
            return type;
        }

        public BasicLanguageData setType(Type type) {
            this.type = type;
            return this;
        }

        public BasicLanguageData setScripts(String scriptTokens) {
            return setScripts(scriptTokens == null ? null : Arrays
                .asList(WHITESPACE_PATTERN.split(scriptTokens)));
        }

        public BasicLanguageData setTerritories(String territoryTokens) {
            return setTerritories(territoryTokens == null ? null : Arrays
                .asList(WHITESPACE_PATTERN.split(territoryTokens)));
        }

        public BasicLanguageData setScripts(Collection<String> scriptTokens) {
            if (frozen) {
                throw new UnsupportedOperationException();
            }
            // TODO add error checking
            scripts = Collections.emptySet();
            if (scriptTokens != null) {
                for (String script : scriptTokens) {
                    addScript(script);
                }
            }
            return this;
        }

        public BasicLanguageData setTerritories(Collection<String> territoryTokens) {
            if (frozen) {
                throw new UnsupportedOperationException();
            }
            territories = Collections.emptySet();
            if (territoryTokens != null) {
                for (String territory : territoryTokens) {
                    addTerritory(territory);
                }
            }
            return this;
        }

        public BasicLanguageData set(BasicLanguageData other) {
            scripts = other.scripts;
            territories = other.territories;
            return this;
        }

        public Set<String> getScripts() {
            return scripts;
        }

        public Set<String> getTerritories() {
            return territories;
        }

        public String toString(String languageSubtag) {
            if (scripts.size() == 0 && territories.size() == 0)
                return "";
            return "\t\t<language type=\""
                + languageSubtag
                + "\""
                + (scripts.size() == 0 ? "" : " scripts=\""
                    + CldrUtility.join(scripts, " ") + "\"")
                + (territories.size() == 0 ? "" : " territories=\""
                    + CldrUtility.join(territories, " ") + "\"")
                + (type == Type.primary ? "" : " alt=\"" + type + "\"") + "/>";
        }

        public String toString() {
            return "[" + type
                + (scripts.isEmpty() ? "" : "; scripts=" + CollectionUtilities.join(scripts, " "))
                + (scripts.isEmpty() ? "" : "; territories=" + CollectionUtilities.join(territories, " "))
                + "]";
        }

        public int compareTo(BasicLanguageData o) {
            int result;
            if (0 != (result = type.compareTo(o.type)))
                return result;
            if (0 != (result = IterableComparator.compareIterables(scripts, o.scripts)))
                return result;
            if (0 != (result = IterableComparator.compareIterables(territories, o.territories)))
                return result;
            return 0;
        }

        public boolean equals(Object input) {
            return compareTo((BasicLanguageData) input) == 0;
        }

        @Override
        public int hashCode() {
            // TODO Auto-generated method stub
            return ((type.ordinal() * 37 + scripts.hashCode()) * 37) + territories.hashCode();
        }

        public BasicLanguageData addScript(String script) {
            // simple error checking
            if (script.length() != 4) {
                throw new IllegalArgumentException("Illegal Script: " + script);
            }
            if (scripts == Collections.EMPTY_SET) {
                scripts = new TreeSet<String>();
            }
            scripts.add(script);
            return this;
        }

        public BasicLanguageData addTerritory(String territory) {
            // simple error checking
            if (territory.length() != 2) {
                throw new IllegalArgumentException("Illegal Territory: " + territory);
            }
            if (territories == Collections.EMPTY_SET) {
                territories = new TreeSet<String>();
            }
            territories.add(territory);
            return this;
        }

        boolean frozen = false;

        public boolean isFrozen() {
            return frozen;
        }

        public BasicLanguageData freeze() {
            frozen = true;
            if (scripts != Collections.EMPTY_SET) {
                scripts = Collections.unmodifiableSet(scripts);
            }
            if (territories != Collections.EMPTY_SET) {
                territories = Collections.unmodifiableSet(territories);
            }
            return this;
        }

        public BasicLanguageData cloneAsThawed() {
            throw new UnsupportedOperationException();
        }

        public void addScripts(Set<String> scripts2) {
            for (String script : scripts2) {
                addScript(script);
            }
        }
    }

    /**
     * Information about currency digits and rounding.
     */
    public static class CurrencyNumberInfo {
        public final int digits;
        public final int rounding;
        public final double roundingIncrement;
        public final int cashRounding;
        public final double cashRoundingIncrement;

        public int getDigits() {
            return digits;
        }

        public int getRounding() {
            return rounding;
        }

        public double getRoundingIncrement() {
            return roundingIncrement;
        }

        public CurrencyNumberInfo(int digits, int rounding, int cashRounding) {
            this.digits = digits;
            this.rounding = rounding;
            roundingIncrement = rounding * Math.pow(10.0, -digits);
            this.cashRounding = cashRounding;
            cashRoundingIncrement = cashRounding * Math.pow(10.0, -digits);
        }
    }

    public static class NumberingSystemInfo {
        public enum NumberingSystemType {
            algorithmic, numeric, unknown
        };

        public final String name;
        public final NumberingSystemType type;
        public final String digits;
        public final String rules;

        public NumberingSystemInfo(XPathParts parts) {
            name = parts.getAttributeValue(-1, "id");
            digits = parts.getAttributeValue(-1, "digits");
            rules = parts.getAttributeValue(-1, "rules");
            type = NumberingSystemType.valueOf(parts.getAttributeValue(-1, "type"));
        }

    }

    /**
     * Class for a range of two dates, refactored to share code.
     * 
     * @author markdavis
     */
    public static final class DateRange implements Comparable<DateRange> {
        static final long START_OF_TIME = Long.MIN_VALUE;
        static final long END_OF_TIME = Long.MAX_VALUE;
        private final long from;
        private final long to;

        public DateRange(String fromString, String toString) {
            from = parseDate(fromString, START_OF_TIME);
            to = parseDate(toString, END_OF_TIME);
        }

        public long getFrom() {
            return from;
        }

        public long getTo() {
            return to;
        }

        static final DateFormat[] simpleFormats = {
            new SimpleDateFormat("yyyy-MM-dd HH:mm"),
            new SimpleDateFormat("yyyy-MM-dd"),
            new SimpleDateFormat("yyyy-MM"),
            new SimpleDateFormat("yyyy"),
        };
        static {
            TimeZone gmt = TimeZone.getTimeZone("GMT");
            for (DateFormat format : simpleFormats) {
                format.setTimeZone(gmt);
            }
        }

        long parseDate(String dateString, long defaultDate) {
            if (dateString == null) {
                return defaultDate;
            }
            ParseException e2 = null;
            for (int i = 0; i < simpleFormats.length; ++i) {
                try {
                    synchronized (simpleFormats[i]) {
                        Date result = simpleFormats[i].parse(dateString);
                        return result.getTime();
                    }
                } catch (ParseException e) {
                    if (e2 == null) {
                        e2 = e;
                    }
                }
            }
            throw new IllegalArgumentException(e2);
        }

        public String toString() {
            return "{" + formatDate(from)
                + ", "
                + formatDate(to) + "}";
        }

        public static String formatDate(long date) {
            if (date == START_OF_TIME) {
                return "-∞";
            }
            if (date == END_OF_TIME) {
                return "∞";
            }
            synchronized (simpleFormats[0]) {
                return simpleFormats[0].format(date);
            }
        }

        @Override
        public int compareTo(DateRange arg0) {
            return to > arg0.to ? 1 : to < arg0.to ? -1 : from > arg0.from ? 1 : from < arg0.from ? -1 : 0;
        }
    }

    /**
     * Information about when currencies are in use in territories
     */
    public static class CurrencyDateInfo implements Comparable<CurrencyDateInfo> {

        public static final Date END_OF_TIME = new Date(DateRange.END_OF_TIME);
        public static final Date START_OF_TIME = new Date(DateRange.START_OF_TIME);

        private String currency;
        private DateRange dateRange;
        private boolean isLegalTender;
        private String errors = "";

        public CurrencyDateInfo(String currency, String startDate, String endDate, String tender) {
            this.currency = currency;
            this.dateRange = new DateRange(startDate, endDate);
            this.isLegalTender = (tender == null || !tender.equals("false"));
        }

        public String getCurrency() {
            return currency;
        }

        public Date getStart() {
            return new Date(dateRange.getFrom());
        }

        public Date getEnd() {
            return new Date(dateRange.getTo());
        }

        public String getErrors() {
            return errors;
        }

        public boolean isLegalTender() {
            return isLegalTender;
        }

        public int compareTo(CurrencyDateInfo o) {
            int result = dateRange.compareTo(o.dateRange);
            if (result != 0) return result;
            return currency.compareTo(o.currency);
        }

        public String toString() {
            return "{" + dateRange + ", " + currency + "}";
        }

        public static String formatDate(Date date) {
            return DateRange.formatDate(date.getTime());
        }

    }

    public static final class MetaZoneRange implements Comparable<MetaZoneRange> {
        final DateRange dateRange;
        final String metazone;

        /**
         * @param metazone
         * @param from
         * @param to
         */
        public MetaZoneRange(String metazone, String fromString, String toString) {
            super();
            this.metazone = metazone;
            dateRange = new DateRange(fromString, toString);
        }

        @Override
        public int compareTo(MetaZoneRange arg0) {
            int result;
            if (0 != (result = dateRange.compareTo(arg0.dateRange))) {
                return result;
            }
            return metazone.compareTo(arg0.metazone);
        }

        public String toString() {
            return "{" + dateRange + ", " + metazone + "}";
        }
    }

    /**
     * Information about telephone code(s) for a given territory
     */
    public static class TelephoneCodeInfo implements Comparable<TelephoneCodeInfo> {
        public static final Date END_OF_TIME = new Date(Long.MAX_VALUE);
        public static final Date START_OF_TIME = new Date(Long.MIN_VALUE);
        private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        private String code;
        private Date start;
        private Date end;
        private String alt;
        private String errors = "";

        // code must not be null, the others can be
        public TelephoneCodeInfo(String code, String startDate, String endDate, String alt) {
            if (code == null)
                throw new NullPointerException();
            this.code = code; // code will not be null
            this.start = parseDate(startDate, START_OF_TIME); // start will not be null
            this.end = parseDate(endDate, END_OF_TIME); // end willl not be null
            this.alt = (alt == null) ? "" : alt; // alt will not be null
        }

        static DateFormat[] simpleFormats = {
            new SimpleDateFormat("yyyy-MM-dd"),
            new SimpleDateFormat("yyyy-MM"),
            new SimpleDateFormat("yyyy"), };

        Date parseDate(String dateString, Date defaultDate) {
            if (dateString == null) {
                return defaultDate;
            }
            ParseException e2 = null;
            for (int i = 0; i < simpleFormats.length; ++i) {
                try {
                    Date result = simpleFormats[i].parse(dateString);
                    return result;
                } catch (ParseException e) {
                    if (i == 0) {
                        errors += dateString + " ";
                    }
                    if (e2 == null) {
                        e2 = e;
                    }
                }
            }
            throw (IllegalArgumentException) new IllegalArgumentException().initCause(e2);
        }

        public String getCode() {
            return code;
        }

        public Date getStart() {
            return start;
        }

        public Date getEnd() {
            return end;
        }

        public String getAlt() {
            return alt; // may return null
        }

        public String getErrors() {
            return errors;
        }

        public boolean equals(Object o) {
            if (!(o instanceof TelephoneCodeInfo))
                return false;
            TelephoneCodeInfo tc = (TelephoneCodeInfo) o;
            return tc.code.equals(code) && tc.start.equals(start) && tc.end.equals(end) && tc.alt.equals(alt);
        }

        public int hashCode() {
            return 31 * code.hashCode() + start.hashCode() + end.hashCode() + alt.hashCode();
        }

        public int compareTo(TelephoneCodeInfo o) {
            int result = code.compareTo(o.code);
            if (result != 0) return result;
            result = start.compareTo(o.start);
            if (result != 0) return result;
            result = end.compareTo(o.end);
            if (result != 0) return result;
            return alt.compareTo(o.alt);
        }

        public String toString() {
            return "{" + code + ", " + formatDate(start) + ", " + formatDate(end) + ", " + alt + "}";
        }

        public static String formatDate(Date date) {
            if (date.equals(START_OF_TIME)) return "-∞";
            if (date.equals(END_OF_TIME)) return "∞";
            return dateFormat.format(date);
        }
    }

    public static class CoverageLevelInfo implements Comparable<CoverageLevelInfo> {
        public final String match;
        public final Level value;
        public final String inLanguage;
        public final Set<String> inLanguageSet;
        public final String inScript;
        public final Set<String> inScriptSet;
        public final String inTerritory;
        public final Set<String> inTerritorySet;
        private Set<String> inTerritorySetInternal;

        public CoverageLevelInfo(String match, int value, String language, String script, String territory) {
            this.inLanguage = language;
            this.inScript = script;
            this.inTerritory = territory;
            this.inLanguageSet = toSet(language);
            this.inScriptSet = toSet(script);
            this.inTerritorySet = toSet(territory); // MUST BE LAST, sets inTerritorySetInternal
            this.match = match;
            this.value = Level.fromLevel(value);
        }

        public static final Pattern NON_ASCII_LETTER = Pattern.compile("[^A-Za-z]+");

        private Set<String> toSet(String source) {
            if (source == null) {
                return null;
            }
            Set<String> result = new HashSet<String>(Arrays.asList(NON_ASCII_LETTER.split(source)));
            result.remove("");
            inTerritorySetInternal = result;
            return Collections.unmodifiableSet(result);
        }

        public int compareTo(CoverageLevelInfo o) {
            if (value == o.value) {
                return match.compareTo(o.match);
            } else {
                return value.compareTo(o.value);
            }
        }

        static void fixEU(Collection<CoverageLevelInfo> targets, SupplementalDataInfo info) {
            Set<String> euCountries = info.getContained("EU");
            for (CoverageLevelInfo item : targets) {
                if (item.inTerritorySet != null
                    && item.inTerritorySet.contains("EU")) {
                    item.inTerritorySetInternal.addAll(euCountries);
                }
            }
        }
    }

    public static final String STAR = "*";
    public static final Set<String> STAR_SET = Builder.with(new HashSet<String>()).add("*").freeze();

    private Map<String, PopulationData> territoryToPopulationData = new TreeMap<String, PopulationData>();

    private Map<String, Map<String, PopulationData>> territoryToLanguageToPopulationData = new TreeMap<String, Map<String, PopulationData>>();

    private Map<String, PopulationData> languageToPopulation = new TreeMap<String, PopulationData>();

    private Map<String, PopulationData> baseLanguageToPopulation = new TreeMap<String, PopulationData>();

    private Relation<String, String> languageToScriptVariants = Relation.of(new TreeMap<String, Set<String>>(),
        TreeSet.class);

    private Relation<String, String> languageToTerritories = Relation.of(new TreeMap<String, Set<String>>(),
        LinkedHashSet.class);

    transient private Relation<String, Pair<Boolean, Pair<Double, String>>> languageToTerritories2 =
        Relation.of(new TreeMap<String, Set<Pair<Boolean, Pair<Double, String>>>>(), TreeSet.class);

    private Map<String, Map<BasicLanguageData.Type, BasicLanguageData>> languageToBasicLanguageData =
        new TreeMap<String, Map<BasicLanguageData.Type, BasicLanguageData>>();

    // private Map<String, BasicLanguageData> languageToBasicLanguageData2 = new
    // TreeMap();

    // Relation(new TreeMap(), TreeSet.class, null);

    private Set<String> allLanguages = new TreeSet<String>();

    private Relation<String, String> containment = Relation.of(new LinkedHashMap<String, Set<String>>(),
        LinkedHashSet.class);
    private Relation<String, String> containmentCore = Relation.of(new LinkedHashMap<String, Set<String>>(),
        LinkedHashSet.class);
    private Relation<String, String> containmentNonDeprecated = Relation.of(new LinkedHashMap<String, Set<String>>(),
        LinkedHashSet.class);

    private Map<String, CurrencyNumberInfo> currencyToCurrencyNumberInfo = new TreeMap<String, CurrencyNumberInfo>();

    private Relation<String, CurrencyDateInfo> territoryToCurrencyDateInfo = Relation.of(
        new TreeMap<String, Set<CurrencyDateInfo>>(), LinkedHashSet.class);

    // private Relation<String, TelephoneCodeInfo> territoryToTelephoneCodeInfo = new Relation(new TreeMap(),
    // LinkedHashSet.class);
    private Map<String, Set<TelephoneCodeInfo>> territoryToTelephoneCodeInfo = new TreeMap<String, Set<TelephoneCodeInfo>>();

    private Set<String> multizone = new TreeSet<String>();

    private Map<String, String> zone_territory = new TreeMap<String, String>();

    private Relation<String, String> zone_aliases = Relation
        .of(new TreeMap<String, Set<String>>(), LinkedHashSet.class);

    private Map<String, Map<String, Map<String, String>>> typeToZoneToRegionToZone = new TreeMap<String, Map<String, Map<String, String>>>();
    private Relation<String, MetaZoneRange> zoneToMetaZoneRanges = Relation.of(
        new TreeMap<String, Set<MetaZoneRange>>(), TreeSet.class);
    private Map<String, Map<String, Relation<String, String>>> deprecated = new HashMap<String, Map<String, Relation<String, String>>>();

    private Map<String, String> metazoneContinentMap = new HashMap<String, String>();
    private Set<String> allMetazones = new TreeSet<String>();

    private Map<String, String> alias_zone = new TreeMap<String, String>();

    public Relation<String, Integer> numericTerritoryMapping = Relation.of(new HashMap<String, Set<Integer>>(),
        HashSet.class);

    public Relation<String, String> alpha3TerritoryMapping = Relation.of(new HashMap<String, Set<String>>(),
        HashSet.class);

    static Map<String, SupplementalDataInfo> directory_instance = new HashMap<String, SupplementalDataInfo>();

    public Map<String, Map<String, Row.R2<List<String>, String>>> typeToTagToReplacement = new TreeMap<String, Map<String, Row.R2<List<String>, String>>>();

    Map<String, List<Row.R4<String, String, Integer, Boolean>>> languageMatch = new HashMap<String, List<Row.R4<String, String, Integer, Boolean>>>();

    public Relation<String, String> bcp47Key2Subtypes = Relation.of(new TreeMap<String, Set<String>>(), TreeSet.class);
    public Relation<String, String> bcp47Extension2Keys = Relation
        .of(new TreeMap<String, Set<String>>(), TreeSet.class);
    public Relation<Row.R2<String, String>, String> bcp47Aliases = Relation.of(
        new TreeMap<Row.R2<String, String>, Set<String>>(), LinkedHashSet.class);
    public Map<Row.R2<String, String>, String> bcp47Descriptions = new TreeMap<Row.R2<String, String>, String>();
    public Map<Row.R2<String, String>, String> bcp47Since = new TreeMap<Row.R2<String, String>, String>();

    public Map<String, Row.R2<String, String>> validityInfo = new HashMap<String, Row.R2<String, String>>();

    public enum MeasurementType {
        measurementSystem, paperSize
    }

    Map<MeasurementType, Map<String, String>> measurementData = new HashMap<MeasurementType, Map<String, String>>();
    Map<String, PreferredAndAllowedHour> timeData = new HashMap<String, PreferredAndAllowedHour>();

    public Relation<String, String> getAlpha3TerritoryMapping() {
        return alpha3TerritoryMapping;
    }

    public Relation<String, Integer> getNumericTerritoryMapping() {
        return numericTerritoryMapping;
    }

    /**
     * Returns type -> tag -> <replacementList, reason>, like "language" -> "sh" -> <{"sr_Latn"}, reason>
     * 
     * @return
     */
    public Map<String, Map<String, R2<List<String>, String>>> getLocaleAliasInfo() {
        return typeToTagToReplacement;
    }

    public R2<List<String>, String> getDeprecatedInfo(String type, String code) {
        return typeToTagToReplacement.get(type).get(code);
    }

    public static SupplementalDataInfo getInstance(File supplementalDirectory) {
        try {
            return getInstance(supplementalDirectory.getCanonicalPath());
        } catch (IOException e) {
            throw (IllegalArgumentException) new IllegalArgumentException()
                .initCause(e);
        }
    }

    static private SupplementalDataInfo defaultInstance = null;

    /**
     * Get an instance chosen using setAsDefaultInstance(), otherwise return an instance using the default directory
     * CldrUtility.SUPPLEMENTAL_DIRECTORY
     * 
     * @return
     */
    public static SupplementalDataInfo getInstance() {
        if (defaultInstance != null) return defaultInstance;
        return CLDRConfig.getInstance().getSupplementalDataInfo();
        // return getInstance(CldrUtility.SUPPLEMENTAL_DIRECTORY);
    }

    /**
     * Mark this as the default instance to be returned by getInstance()
     */
    public void setAsDefaultInstance() {
        defaultInstance = this;
    }

    public static SupplementalDataInfo getInstance(String supplementalDirectory) {
        synchronized (SupplementalDataInfo.class) {
            SupplementalDataInfo instance = directory_instance
                .get(supplementalDirectory);
            if (instance != null) {
                return instance;
            }
            // canonicalize name & try again
            String canonicalpath;
            try {
                if (supplementalDirectory == null) {
                    throw new IllegalArgumentException("Error: null supplemental directory");
                }
                canonicalpath = new File(supplementalDirectory).getCanonicalPath();
            } catch (IOException e) {
                throw (IllegalArgumentException) new IllegalArgumentException()
                    .initCause(e);
            }
            if (!canonicalpath.equals(supplementalDirectory)) {
                instance = directory_instance.get(canonicalpath);
                if (instance != null) {
                    directory_instance.put(supplementalDirectory, instance);
                    return instance;
                }
            }
            instance = new SupplementalDataInfo();
            MyHandler myHandler = instance.new MyHandler();
            XMLFileReader xfr = new XMLFileReader().setHandler(myHandler);
            File files1[] = new File(canonicalpath).listFiles();
            if (files1 == null) {
                throw new InternalError("Could not list XML Files from " + canonicalpath);
            }
            // get bcp47 files also
            File files2[] = new File(canonicalpath + "/../bcp47").listFiles();

            CBuilder<File, ArrayList<File>> builder = Builder.with(new ArrayList<File>());
            builder.addAll(files1);
            if (files2 != null) {
                builder.addAll(files2);
            }
            for (File file : builder.get()) {
                if (DEBUG) {
                    try {
                        System.out.println(file.getCanonicalPath());
                    } catch (IOException e) {
                    }
                }
                String name = file.toString();
                if (!name.endsWith(".xml")) continue;
                xfr.read(name, -1, true);
                myHandler.cleanup();
            }

            // xfr = new XMLFileReader().setHandler(instance.new MyHandler());
            // .xfr.read(canonicalpath + "/supplementalMetadata.xml", -1, true);

            instance.makeStuffSafe();
            // cache
            directory_instance.put(supplementalDirectory, instance);
            if (!canonicalpath.equals(supplementalDirectory)) {
                directory_instance.put(canonicalpath, instance);
            }
            return instance;
        }
    }

    private SupplementalDataInfo() {
    }; // hide

    private void makeStuffSafe() {
        // now make stuff safe
        allLanguages.addAll(languageToPopulation.keySet());
        allLanguages.addAll(baseLanguageToPopulation.keySet());
        allLanguages = Collections.unmodifiableSet(allLanguages);
        skippedElements = Collections.unmodifiableSet(skippedElements);
        multizone = Collections.unmodifiableSet(multizone);
        zone_territory = Collections.unmodifiableMap(zone_territory);
        alias_zone = Collections.unmodifiableMap(alias_zone);
        references = Collections.unmodifiableMap(references);
        likelySubtags = Collections.unmodifiableMap(likelySubtags);
        currencyToCurrencyNumberInfo = Collections.unmodifiableMap(currencyToCurrencyNumberInfo);
        territoryToCurrencyDateInfo.freeze();
        // territoryToTelephoneCodeInfo.freeze();
        territoryToTelephoneCodeInfo = Collections.unmodifiableMap(territoryToTelephoneCodeInfo);

        typeToZoneToRegionToZone = CldrUtility.protectCollection(typeToZoneToRegionToZone);
        typeToTagToReplacement = CldrUtility.protectCollection(typeToTagToReplacement);

        zoneToMetaZoneRanges.freeze();

        containment.freeze();
        containmentCore.freeze();
        containmentNonDeprecated.freeze();

        CldrUtility.protectCollection(languageToBasicLanguageData);
        for (String language : languageToTerritories2.keySet()) {
            for (Pair<Boolean, Pair<Double, String>> pair : languageToTerritories2.getAll(language)) {
                languageToTerritories.put(language, pair.getSecond().getSecond());
            }
        }
        languageToTerritories2 = null; // free up the memory.
        languageToTerritories.freeze();
        zone_aliases.freeze();
        languageToScriptVariants.freeze();

        numericTerritoryMapping.freeze();
        alpha3TerritoryMapping.freeze();

        // freeze contents
        for (String language : languageToPopulation.keySet()) {
            languageToPopulation.get(language).freeze();
        }
        for (String language : baseLanguageToPopulation.keySet()) {
            baseLanguageToPopulation.get(language).freeze();
        }
        for (String territory : territoryToPopulationData.keySet()) {
            territoryToPopulationData.get(territory).freeze();
        }
        for (String territory : territoryToLanguageToPopulationData.keySet()) {
            Map<String, PopulationData> languageToPopulationDataTemp = territoryToLanguageToPopulationData
                .get(territory);
            for (String language : languageToPopulationDataTemp.keySet()) {
                languageToPopulationDataTemp.get(language).freeze();
            }
        }
        localeToPluralInfo = Collections.unmodifiableMap(localeToPluralInfo);
        localeToOrdinalInfo = Collections.unmodifiableMap(localeToOrdinalInfo);

        if (lastDayPeriodLocales != null) {
            addDayPeriodInfo();
        }
        localeToDayPeriodInfo = Collections.unmodifiableMap(localeToDayPeriodInfo);
        languageMatch = CldrUtility.protectCollection(languageMatch);
        bcp47Key2Subtypes.freeze();
        bcp47Extension2Keys.freeze();
        bcp47Aliases.freeze();
        CldrUtility.protectCollection(bcp47Descriptions);

        CoverageLevelInfo.fixEU(coverageLevels, this);
        coverageLevels = Collections.unmodifiableSortedSet(coverageLevels);

        deprecated = CldrUtility.protectCollection(deprecated);
        measurementData = CldrUtility.protectCollection(measurementData);
        timeData = CldrUtility.protectCollection(timeData);

        validityInfo = CldrUtility.protectCollection(validityInfo);
    }

    // private Map<String, Map<String, String>> makeUnmodifiable(Map<String, Map<String, String>>
    // metazoneToRegionToZone) {
    // Map<String, Map<String, String>> temp = metazoneToRegionToZone;
    // for (String mzone : metazoneToRegionToZone.keySet()) {
    // temp.put(mzone, Collections.unmodifiableMap(metazoneToRegionToZone.get(mzone)));
    // }
    // return Collections.unmodifiableMap(temp);
    // }

    /**
     * Core function used to process each of the paths, and add the data to the appropriate data member.
     */
    class MyHandler extends XMLFileReader.SimpleHandler {
        private static final double MAX_POPULATION = 3000000000.0;

        XPathParts parts = new XPathParts();

        LanguageTagParser languageTagParser = new LanguageTagParser();

        /**
         * Finish processing anything left hanging in the file.
         */
        public void cleanup() {
            if (lastPluralMap.size() > 0) {
                addPluralInfo(lastPluralWasOrdinal);
            }
            lastPluralLocales = "root";
        }

        public void handlePathValue(String path, String value) {
            try {
                parts.set(path);
                String level0 = parts.getElement(0);
                String level1 = parts.size() < 2 ? null : parts.getElement(1);
                String level2 = parts.size() < 3 ? null : parts.getElement(2);
                String level3 = parts.size() < 4 ? null : parts.getElement(3);
                // String level4 = parts.size() < 5 ? null : parts.getElement(4);
                if (level1.equals("generation") || level1.equals("version")) {
                    // skip
                    return;
                }

                // copy the rest from ShowLanguages later
                if (level0.equals("ldmlBCP47")) {
                    if (handleBcp47(level2)) {
                        return;
                    }
                } else if (level1.equals("territoryInfo")) {
                    if (handleTerritoryInfo()) {
                        return;
                    }
                } else if (level1.equals("calendarPreferenceData")) {
                    handleCalendarPreferenceData();
                    return;
                } else if (level1.equals("languageData")) {
                    handleLanguageData();
                    return;
                } else if (level1.equals("territoryContainment")) {
                    handleTerritoryContainment();
                    return;
                } else if (level1.equals("currencyData")) {
                    if (handleCurrencyData(level2)) {
                        return;
                    }
                    // } else if (level1.equals("timezoneData")) {
                    // if (handleTimezoneData(level2)) {
                    // return;
                    // }
                } else if ("metazoneInfo".equals(level2)) {
                    if (handleMetazoneInfo(level2, level3)) {
                        return;
                    }
                } else if ("mapTimezones".equals(level2)) {
                    if (handleMetazoneData(level2, level3)) {
                        return;
                    }
                } else if (level1.equals("plurals")) {
                    addPluralPath(parts, value);
                    return;
                } else if (level1.equals("dayPeriodRuleSet")) {
                    addDayPeriodPath(parts, value);
                    return;
                } else if (level1.equals("telephoneCodeData")) {
                    handleTelephoneCodeData(parts);
                    return;
                } else if (level1.equals("references")) {
                    String type = parts.getAttributeValue(-1, "type");
                    String uri = parts.getAttributeValue(-1, "uri");
                    references.put(type, new Pair<String, String>(uri, value).freeze());
                    return;
                } else if (level1.equals("likelySubtags")) {
                    handleLikelySubtags();
                    return;
                } else if (level1.equals("numberingSystems")) {
                    handleNumberingSystems();
                    return;
                } else if (level1.equals("coverageLevels")) {
                    handleCoverageLevels();
                    return;
                } else if (level1.equals("parentLocales")) {
                    handleParentLocales();
                    return;
                } else if (level1.equals("metadata")) {
                    if (handleMetadata(level2, value)) {
                        return;
                    }
                } else if (level1.equals("codeMappings")) {
                    if (handleCodeMappings(level2)) {
                        return;
                    }
                } else if (level1.equals("languageMatching")) {
                    if (handleLanguageMatcher(level2)) {
                        return;
                    }
                } else if (level1.equals("measurementData")) {
                    if (handleMeasurementData(level2)) {
                        return;
                    }
                } else if (level1.equals("timeData")) {
                    if (handleTimeData(level2)) {
                        return;
                    }
                }

                // capture elements we didn't look at, since we should cover everything.
                // this helps for updates

                final String skipKey = level1 + (level2 == null ? "" : "/" + level2);
                if (!skippedElements.contains(skipKey)) {
                    skippedElements.add(skipKey);
                }
                // System.out.println("Skipped Element: " + path);
            } catch (Exception e) {
                throw (IllegalArgumentException) new IllegalArgumentException("path: "
                    + path + ",\tvalue: " + value).initCause(e);
            }
        }

        private boolean handleMeasurementData(String level2) {
            /**
             * <measurementSystem type="US" territories="LR MM US"/>
             * <paperSize type="A4" territories="001"/>
             */
            MeasurementType measurementType = MeasurementType.valueOf(level2);
            String type = parts.getAttributeValue(-1, "type");
            String territories = parts.getAttributeValue(-1, "territories");
            Map<String, String> data = measurementData.get(measurementType);
            if (data == null) {
                measurementData.put(measurementType, data = new HashMap<String, String>());
            }
            for (String territory : territories.trim().split("\\s+")) {
                data.put(territory, type);
            }
            return true;
        }

        private boolean handleTimeData(String level2) {
            /**
             * <hours preferred="H" allowed="H" regions="IL RU"/>
             */
            String preferred = parts.getAttributeValue(-1, "preferred");
            // String[] allowed = parts.getAttributeValue(-1, "allowed").trim().split("\\s+");
            PreferredAndAllowedHour preferredAndAllowedHour = new PreferredAndAllowedHour(preferred,
                parts.getAttributeValue(-1, "allowed"));
            for (String region : parts.getAttributeValue(-1, "regions").trim().split("\\s+")) {
                PreferredAndAllowedHour oldValue = timeData.put(region, preferredAndAllowedHour);
                if (oldValue != null) {
                    throw new IllegalArgumentException("timeData/hours must not have duplicate regions: " + region);
                }
            }
            return true;
        }

        private boolean handleBcp47(String level2) {
            String key = parts.getAttributeValue(2, "name");
            String keyAlias = parts.getAttributeValue(2, "alias");
            String keyDescription = parts.getAttributeValue(2, "description");
            String extension = parts.getAttributeValue(2, "extension");
            if (extension == null) {
                extension = "u";
            }

            bcp47Extension2Keys.put(extension, key);

            if (keyAlias != null) {
                bcp47Aliases.putAll((R2<String, String>) Row.of(key, "").freeze(),
                    Arrays.asList(keyAlias.trim().split("\\s+")));
            }

            if (keyDescription != null) {
                bcp47Descriptions.put((R2<String, String>) Row.of(key, "").freeze(), keyDescription);
            }

            if (parts.size() > 3) { // for parts with no subtype: //ldmlBCP47/keyword/key[@extension="t"][@name="x0"]

                // have subtype
                String subtype = parts.getAttributeValue(3, "name");
                String subtypeAlias = parts.getAttributeValue(3, "alias");
                String subtypeDescription = parts.getAttributeValue(3, "description");
                String subtypeSince = parts.getAttributeValue(3, "since");
                bcp47Key2Subtypes.put(key, subtype);
                if (subtypeAlias != null) {
                    bcp47Aliases.putAll((R2<String, String>) Row.of(key, subtype).freeze(),
                        Arrays.asList(subtypeAlias.trim().split("\\s+")));
                }

                if (subtypeDescription != null) {
                    bcp47Descriptions.put((R2<String, String>) Row.of(key, subtype).freeze(), subtypeDescription);
                }
                if (subtypeDescription != null) {
                    bcp47Since.put((R2<String, String>) Row.of(key, subtype).freeze(), subtypeSince);
                }
            }

            return true;
        }

        private boolean handleLanguageMatcher(String level2) {
            String type = parts.getAttributeValue(2, "type");
            List<R4<String, String, Integer, Boolean>> matches = languageMatch.get(type);
            if (matches == null) {
                languageMatch.put(type, matches = new ArrayList<R4<String, String, Integer, Boolean>>());
            }
            matches.add(Row.of(parts.getAttributeValue(3, "desired"), parts.getAttributeValue(3, "supported"),
                Integer.parseInt(parts.getAttributeValue(3, "percent")),
                "true".equals(parts.getAttributeValue(3, "oneway"))));
            return true;
        }

        private boolean handleCodeMappings(String level2) {
            if (level2.equals("territoryCodes")) {
                // <territoryCodes type="VU" numeric="548" alpha3="VUT"/>
                String type = parts.getAttributeValue(-1, "type");
                final String numeric = parts.getAttributeValue(-1, "numeric");
                if (numeric != null) {
                    numericTerritoryMapping.put(type, Integer.parseInt(numeric));
                }
                final String alpha3 = parts.getAttributeValue(-1, "alpha3");
                if (alpha3 != null) {
                    alpha3TerritoryMapping.put(type, alpha3);
                }
                return true;
            }
            return false;
        }

        private void handleNumberingSystems() {
            NumberingSystemInfo ns = new NumberingSystemInfo(parts);
            numberingSystems.put(ns.name, ns);
            if (ns.type == NumberingSystemType.numeric) {
                numericSystems.add(ns.name);
            }
        }

        private void handleCoverageLevels() {
            if (parts.containsElement("coverageLevel")) {
                String match = parts.containsAttribute("match") ? coverageVariables.replace(parts.getAttributeValue(-1,
                    "match")) : null;
                String valueStr = parts.getAttributeValue(-1, "value");
                String inLanguage = parts.containsAttribute("inLanguage") ? coverageVariables.replace(parts
                    .getAttributeValue(-1, "inLanguage")) : null;
                String inScript = parts.containsAttribute("inScript") ? coverageVariables.replace(parts
                    .getAttributeValue(-1, "inScript")) : null;
                String inTerritory = parts.containsAttribute("inTerritory") ? coverageVariables.replace(parts
                    .getAttributeValue(-1, "inTerritory")) : null;
                Integer value = (valueStr != null) ? Integer.valueOf(valueStr) : Integer.valueOf("101");
                CoverageLevelInfo ci = new CoverageLevelInfo(match, value, inLanguage, inScript, inTerritory);
                coverageLevels.add(ci);
            } else if (parts.containsElement("coverageVariable")) {
                String key = parts.getAttributeValue(-1, "key");
                String value = parts.getAttributeValue(-1, "value");
                coverageVariables.add(key, value);
            }
        }

        private void handleParentLocales() {
            String parent = parts.getAttributeValue(-1, "parent");
            String locales = parts.getAttributeValue(-1, "locales");
            String[] pl = locales.split(" ");
            for (int i = 0; i < pl.length; i++) {
                parentLocales.put(pl[i], parent);
            }
        }

        private void handleCalendarPreferenceData() {
            String territoryString = parts.getAttributeValue(-1, "territories");
            String orderingString = parts.getAttributeValue(-1, "ordering");
            String[] calendars = orderingString.split(" ");
            String[] territories = territoryString.split(" ");
            List<String> calendarList = Arrays.asList(calendars);
            for (int i = 0; i < territories.length; i++) {
                calendarPreferences.put(territories[i], calendarList);
            }
        }

        private void handleLikelySubtags() {
            String from = parts.getAttributeValue(-1, "from");
            String to = parts.getAttributeValue(-1, "to");
            likelySubtags.put(from, to);
        }

        /**
         * Only called if level2 = mapTimezones. Level 1 might be metaZones or might be windowsZones
         */
        private boolean handleMetazoneData(String level2, String level3) {
            if (level3.equals("mapZone")) {
                String maintype = parts.getAttributeValue(2, "type");
                if (maintype == null) {
                    maintype = "windows";
                }
                String mzone = parts.getAttributeValue(3, "other");
                String region = parts.getAttributeValue(3, "territory");
                String zone = parts.getAttributeValue(3, "type");

                Map<String, Map<String, String>> zoneToRegionToZone = typeToZoneToRegionToZone.get(maintype);
                if (zoneToRegionToZone == null) {
                    typeToZoneToRegionToZone.put(maintype,
                        zoneToRegionToZone = new TreeMap<String, Map<String, String>>());
                }
                Map<String, String> regionToZone = zoneToRegionToZone.get(mzone);
                if (regionToZone == null) {
                    zoneToRegionToZone.put(mzone, regionToZone = new TreeMap<String, String>());
                }
                if (region != null) {
                    regionToZone.put(region, zone);
                }
                if (maintype.equals("metazones")) {
                    if (mzone != null && region.equals("001")) {
                        metazoneContinentMap.put(mzone, zone.substring(0, zone.indexOf("/")));
                    }
                    allMetazones.add(mzone);
                }
                return true;
            }
            return false;
        }

        private Collection<String> getSpaceDelimited(int index, String attribute, Collection<String> defaultValue) {
            String temp = parts.getAttributeValue(index, attribute);
            Collection<String> elements = temp == null ? defaultValue : Arrays.asList(temp.split("\\s+"));
            return elements;
        }

        /*
         * 
         * <supplementalData>
         * <metaZones>
         * <metazoneInfo>
         * <timezone type="Asia/Yerevan">
         * <usesMetazone to="1991-09-22 20:00" mzone="Yerevan"/>
         * <usesMetazone from="1991-09-22 20:00" mzone="Armenia"/>
         */

        private boolean handleMetazoneInfo(String level2, String level3) {
            if (level3.equals("timezone")) {
                String zone = parts.getAttributeValue(3, "type");
                String mzone = parts.getAttributeValue(4, "mzone");
                String from = parts.getAttributeValue(4, "from");
                String to = parts.getAttributeValue(4, "to");
                MetaZoneRange mzoneRange = new MetaZoneRange(mzone, from, to);
                zoneToMetaZoneRanges.put(zone, mzoneRange);
                return true;
            }
            return false;
        }

        private boolean handleMetadata(String level2, String value) {
            if (parts.contains("defaultContent")) {
                String defContent = parts.getAttributeValue(-1, "locales").trim();
                String[] defLocales = defContent.split("\\s+");
                defaultContentLocales = Collections.unmodifiableSet(new TreeSet<String>(Arrays.asList(defLocales)));

                return true;
            }
            if (level2.equals("alias")) {
                // <alias>
                // <!-- grandfathered 3066 codes -->
                // <languageAlias type="art-lojban" replacement="jbo"/> <!-- Lojban -->
                String level3 = parts.getElement(3);
                if (!level3.endsWith("Alias")) {
                    throw new IllegalArgumentException();
                }
                level3 = level3.substring(0, level3.length() - "Alias".length());
                Map<String, R2<List<String>, String>> tagToReplacement = typeToTagToReplacement.get(level3);
                if (tagToReplacement == null) {
                    typeToTagToReplacement.put(level3,
                        tagToReplacement = new TreeMap<String, R2<List<String>, String>>());
                }
                final String replacement = parts.getAttributeValue(3, "replacement");
                final String reason = parts.getAttributeValue(3, "reason");
                List<String> replacementList = replacement == null ? null : Arrays.asList(replacement.replace("-", "_")
                    .split("\\s+"));
                String cleanTag = parts.getAttributeValue(3, "type").replace("-", "_");
                tagToReplacement.put(cleanTag, (R2<List<String>, String>) Row.of(replacementList, reason).freeze());
                return true;
            } else if (level2.equals("validity")) {
                // <variable id="$grandfathered" type="choice">
                String level3 = parts.getElement(3);
                if (level3.equals("variable")) {
                    Map<String, String> attributes = parts.getAttributes(-1);
                    validityInfo.put(attributes.get("id"), Row.of(attributes.get("type"), value));
                    if ("$language".equals(attributes.get("id")) && "choice".equals(attributes.get("type"))) {
                        String[] validCodeArray = value.trim().split("\\s+");
                        CLDRLanguageCodes = Collections.unmodifiableSet(new TreeSet<String>(Arrays
                            .asList(validCodeArray)));
                    }
                    if ("$script".equals(attributes.get("id")) && "choice".equals(attributes.get("type"))) {
                        String[] validCodeArray = value.trim().split("\\s+");
                        CLDRScriptCodes = Collections
                            .unmodifiableSet(new TreeSet<String>(Arrays.asList(validCodeArray)));
                    }
                    return true;
                }
            } else if (level2.equals("attributeOrder")) {
                attributeOrder = Arrays.asList(value.trim().split("\\s+"));
                return true;
            } else if (level2.equals("elementOrder")) {
                elementOrder = Arrays.asList(value.trim().split("\\s+"));
                return true;
            } else if (level2.equals("serialElements")) {
                serialElements = Arrays.asList(value.trim().split("\\s+"));
                return true;
            } else if (level2.equals("deprecated")) {
                // <deprecated>
                // <deprecatedItems
                // elements="monthNames monthAbbr localizedPatternChars week minDays firstDay weekendStart weekendEnd yesexpr noexpr measurement hoursFormat abbreviationFallback preferenceOrdering dateRangePattern"/>
                // <deprecatedItems type="supplemental" elements="calendar" attributes="territories"/>

                // Map<String, String> attributeSet = parts.getAttributes(-1);
                Collection<String> types = getSpaceDelimited(-1, "type", STAR_SET);
                Collection<String> elements = getSpaceDelimited(-1, "elements", STAR_SET);
                Collection<String> attributes = getSpaceDelimited(-1, "attributes", STAR_SET);
                Collection<String> values = getSpaceDelimited(-1, "values", STAR_SET);
                for (String type : types) {
                    Map<String, Relation<String, String>> dep = deprecated.get(type);
                    if (dep == null) {
                        deprecated.put(type, dep = new HashMap<String, Relation<String, String>>());
                    }
                    for (String element : elements) {
                        Relation<String, String> attribute2Values = dep.get(element);
                        if (attribute2Values == null) {
                            dep.put(element,
                                attribute2Values = Relation.of(new HashMap<String, Set<String>>(), TreeSet.class));
                        }
                        for (String attribute : attributes) {
                            for (String value0 : values) {
                                attribute2Values.put(attribute, value0);
                            }
                        }
                    }
                }
            } else if (level2.equals("distinguishing")) {
                String level3 = parts.getElement(3);
                if (level3.equals("distinguishingItems")) {
                    Map<String, String> attributes = parts.getAttributes(-1);
                    // <distinguishingItems
                    // attributes="key request id _q registry alt iso4217 iso3166 mzone from to type numberSystem"/>
                    // <distinguishingItems exclude="true"
                    // elements="default measurementSystem mapping abbreviationFallback preferenceOrdering"
                    // attributes="type"/>

                    if (attributes.containsKey("exclude") && "true".equals(attributes.get("exclude"))) {
                        return false; // don't handle the excludes -yet.
                    } else {
                        distinguishingAttributes = Collections.unmodifiableCollection(getSpaceDelimited(-1,
                            "attributes", STAR_SET));
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean handleTerritoryInfo() {

            // <territoryInfo>
            // <territory type="AD" gdp="1840000000" literacyPercent="100"
            // population="66000"> <!--Andorra-->
            // <languagePopulation type="ca" populationPercent="50"/>
            // <!--Catalan-->

            Map<String, String> territoryAttributes = parts.getAttributes(2);
            String territory = territoryAttributes.get("type");
            double territoryPopulation = parseDouble(territoryAttributes.get("population"));
            if (failsRangeCheck("population", territoryPopulation, 0, MAX_POPULATION)) {
                return true;
            }

            double territoryLiteracyPercent = parseDouble(territoryAttributes.get("literacyPercent"));
            double territoryGdp = parseDouble(territoryAttributes.get("gdp"));
            if (territoryToPopulationData.get(territory) == null) {
                territoryToPopulationData.put(territory, new PopulationData()
                    .setPopulation(territoryPopulation)
                    .setLiteratePopulation(territoryLiteracyPercent * territoryPopulation / 100)
                    .setGdp(territoryGdp));
            }
            if (parts.size() > 3) {

                Map<String, String> languageInTerritoryAttributes = parts
                    .getAttributes(3);
                String language = languageInTerritoryAttributes.get("type");
                double languageLiteracyPercent = parseDouble(languageInTerritoryAttributes.get("writingPercent"));
                if (Double.isNaN(languageLiteracyPercent)) {
                    languageLiteracyPercent = territoryLiteracyPercent;
                }// else {
                 // System.out.println("writingPercent\t" + languageLiteracyPercent
                 // + "\tterritory\t" + territory
                 // + "\tlanguage\t" + language);
                 // }
                double languagePopulationPercent = parseDouble(languageInTerritoryAttributes.get("populationPercent"));
                double languagePopulation = languagePopulationPercent * territoryPopulation / 100;
                // double languageGdp = languagePopulationPercent * territoryGdp;

                // store
                Map<String, PopulationData> territoryLanguageToPopulation = territoryToLanguageToPopulationData
                    .get(territory);
                if (territoryLanguageToPopulation == null) {
                    territoryToLanguageToPopulationData.put(territory,
                        territoryLanguageToPopulation = new TreeMap<String, PopulationData>());
                }
                OfficialStatus officialStatus = OfficialStatus.unknown;
                String officialStatusString = languageInTerritoryAttributes.get("officialStatus");
                if (officialStatusString != null) officialStatus = OfficialStatus.valueOf(officialStatusString);

                PopulationData newData = new PopulationData()
                    .setPopulation(languagePopulation)
                    .setLiteratePopulation(languageLiteracyPercent * languagePopulation / 100)
                    .setOfficialStatus(officialStatus)
                // .setGdp(languageGdp)
                ;
                newData.freeze();
                if (territoryLanguageToPopulation.get(language) != null) {
                    System.out
                        .println("Internal Problem in supplementalData: multiple data items for "
                            + language + ", " + territory + "\tSkipping " + newData);
                    return true;
                }

                territoryLanguageToPopulation.put(language, newData);
                // add the language, using the Pair fields to get the ordering right
                languageToTerritories2.put(language,
                    Pair.of(newData.getOfficialStatus().isMajor() ? false : true,
                        Pair.of(-newData.getLiteratePopulation(), territory)));

                // now collect data for languages globally
                PopulationData data = languageToPopulation.get(language);
                if (data == null) {
                    languageToPopulation.put(language, data = new PopulationData().set(newData));
                } else {
                    data.add(newData);
                }
                // if (language.equals("en")) {
                // System.out.println(territory + "\tnewData:\t" + newData + "\tdata:\t" + data);
                // }
                String baseLanguage = languageTagParser.set(language).getLanguage();
                if (!baseLanguage.equals(language)) {
                    languageToScriptVariants.put(baseLanguage, language);

                    data = baseLanguageToPopulation.get(baseLanguage);
                    if (data == null)
                        baseLanguageToPopulation.put(baseLanguage,
                            data = new PopulationData());
                    data.add(newData);
                }
            }
            return true;
        }

        private boolean handleCurrencyData(String level2) {
            if (level2.equals("fractions")) {
                // <info iso4217="ADP" digits="0" rounding="0" cashRounding="5"/>
                currencyToCurrencyNumberInfo.put(parts.getAttributeValue(3, "iso4217"),
                    new CurrencyNumberInfo(
                        parseIntegerOrNull(parts.getAttributeValue(3, "digits")),
                        parseIntegerOrNull(parts.getAttributeValue(3, "rounding")),
                        parseIntegerOrNull(parts.getAttributeValue(3, "cashRounding")))
                    );
                return true;
            }
            /*
             * <region iso3166="AD">
             * <currency iso4217="EUR" from="1999-01-01"/>
             * <currency iso4217="ESP" from="1873" to="2002-02-28"/>
             */
            if (level2.equals("region")) {
                territoryToCurrencyDateInfo.put(parts.getAttributeValue(2, "iso3166"),
                    new CurrencyDateInfo(parts.getAttributeValue(3, "iso4217"),
                        parts.getAttributeValue(3, "from"),
                        parts.getAttributeValue(3, "to"),
                        parts.getAttributeValue(3, "tender")));
                return true;
            }

            return false;
        }

        private void handleTelephoneCodeData(XPathParts parts) {
            // element 2: codesByTerritory territory [draft] [references]
            String terr = parts.getAttributeValue(2, "territory");
            // element 3: telephoneCountryCode code [from] [to] [draft] [references] [alt]
            TelephoneCodeInfo tcInfo = new TelephoneCodeInfo(parts.getAttributeValue(3, "code"),
                parts.getAttributeValue(3, "from"),
                parts.getAttributeValue(3, "to"),
                parts.getAttributeValue(3, "alt"));

            Set<TelephoneCodeInfo> tcSet = territoryToTelephoneCodeInfo.get(terr);
            if (tcSet == null) {
                tcSet = new LinkedHashSet<TelephoneCodeInfo>();
                territoryToTelephoneCodeInfo.put(terr, tcSet);
            }
            tcSet.add(tcInfo);
        }

        private void handleTerritoryContainment() {
            // <group type="001" contains="002 009 019 142 150"/>
            final String container = parts.getAttributeValue(-1, "type");
            final List<String> contained = Arrays
                .asList(parts.getAttributeValue(-1, "contains").split("\\s+"));
            containment.putAll(container, contained);
            String deprecatedAttribute = parts.getAttributeValue(-1, "status");
            String grouping = parts.getAttributeValue(-1, "grouping");
            if (deprecatedAttribute == null) {
                containmentNonDeprecated.putAll(container, contained);
                if (grouping == null) {
                    containmentCore.putAll(container, contained);
                }
            }
        }

        private void handleLanguageData() {
            // <languageData>
            // <language type="aa" scripts="Latn" territories="DJ ER ET"/> <!--
            // Reflecting submitted data, cldrbug #1013 -->
            // <language type="ab" scripts="Cyrl" territories="GE"
            // alt="secondary"/>
            String language = (String) parts.getAttributeValue(2, "type");
            BasicLanguageData languageData = new BasicLanguageData();
            languageData
                .setType(parts.getAttributeValue(2, "alt") == null ? BasicLanguageData.Type.primary
                    : BasicLanguageData.Type.secondary);
            languageData.setScripts(parts.getAttributeValue(2, "scripts"))
                .setTerritories(parts.getAttributeValue(2, "territories"));
            Map<Type, BasicLanguageData> map = languageToBasicLanguageData.get(language);
            if (map == null) {
                languageToBasicLanguageData.put(language, map = new EnumMap<Type, BasicLanguageData>(
                    BasicLanguageData.Type.class));
            }
            if (map.containsKey(languageData.type)) {
                throw new IllegalArgumentException("Duplicate value:\t" + parts);
            }
            map.put(languageData.type, languageData);
        }

        private boolean failsRangeCheck(String path, double input, double min, double max) {
            if (input >= min && input <= max) {
                return false;
            }
            System.out
                .println("Internal Problem in supplementalData: range check fails for "
                    + input + ", min: " + min + ", max:" + max + "\t" + path);

            return false;
        }

        private double parseDouble(String literacyString) {
            return literacyString == null ? Double.NaN : Double
                .parseDouble(literacyString);
        }
    }

    public class CoverageVariableInfo {
        public Set<String> targetScripts;
        public Set<String> targetTerritories;
        public Set<String> calendars;
        public Set<String> targetCurrencies;
        public Set<String> targetTimeZones;
        public Set<String> targetPlurals;
    }

    public static String toRegexString(Set<String> s) {
        Iterator<String> it = s.iterator();
        StringBuilder sb = new StringBuilder("(");
        int count = 0;
        while (it.hasNext()) {
            if (count > 0) {
                sb.append("|");
            }
            sb.append(it.next());
            count++;
        }
        sb.append(")");
        return sb.toString();

    }

    public int parseIntegerOrNull(String attributeValue) {
        return attributeValue == null ? 0 : Integer.parseInt(attributeValue);
    }

    Set<String> skippedElements = new TreeSet<String>();

    private Map<String, Pair<String, String>> references = new TreeMap<String, Pair<String, String>>();
    private Map<String, String> likelySubtags = new TreeMap<String, String>();
    // make public temporarily until we resolve.
    private SortedSet<CoverageLevelInfo> coverageLevels = new TreeSet<CoverageLevelInfo>();
    private Map<String, String> parentLocales = new HashMap<String, String>();
    private Map<String, List<String>> calendarPreferences = new HashMap<String, List<String>>();
    private Map<String, CoverageVariableInfo> localeSpecificVariables = new TreeMap<String, CoverageVariableInfo>();
    private VariableReplacer coverageVariables = new VariableReplacer();
    private Map<String, NumberingSystemInfo> numberingSystems = new HashMap<String, NumberingSystemInfo>();
    private Set<String> numericSystems = new TreeSet<String>();
    private Set<String> defaultContentLocales;
    public Map<CLDRLocale, CLDRLocale> baseToDefaultContent; // wo -> wo_Arab_SN
    public Map<CLDRLocale, CLDRLocale> defaultContentToBase; // wo_Arab_SN -> wo
    private Set<String> CLDRLanguageCodes;
    private Set<String> CLDRScriptCodes;

    /**
     * Get the population data for a language. Warning: if the language has script variants, cycle on those variants.
     * 
     * @param language
     * @param output
     * @return
     */
    public PopulationData getLanguagePopulationData(String language) {
        return languageToPopulation.get(language);
    }

    public Set<String> getLanguages() {
        return allLanguages;
    }

    public Set<String> getTerritoryToLanguages(String territory) {
        Map<String, PopulationData> result = territoryToLanguageToPopulationData
            .get(territory);
        if (result == null) {
            return Collections.emptySet();
        }
        return result.keySet();
    }

    public PopulationData getLanguageAndTerritoryPopulationData(String language,
        String territory) {
        Map<String, PopulationData> result = territoryToLanguageToPopulationData
            .get(territory);
        if (result == null) {
            return null;
        }
        return result.get(language);
    }

    public Set<String> getTerritoriesWithPopulationData() {
        return territoryToLanguageToPopulationData.keySet();
    }

    public Set<String> getLanguagesForTerritoryWithPopulationData(String territory) {
        return territoryToLanguageToPopulationData.get(territory).keySet();
    }

    public Set<BasicLanguageData> getBasicLanguageData(String language) {
        Map<Type, BasicLanguageData> map = languageToBasicLanguageData.get(language);
        if (map == null) {
            throw new IllegalArgumentException("Bad language code: " + language);
        }
        return new LinkedHashSet<BasicLanguageData>(map.values());
    }

    public Map<Type, BasicLanguageData> getBasicLanguageDataMap(String language) {
        return languageToBasicLanguageData.get(language);
    }

    public Set<String> getBasicLanguageDataLanguages() {
        return languageToBasicLanguageData.keySet();
    }

    public Relation<String, String> getContainmentCore() {
        return containmentCore;
    }

    public Set<String> getContained(String territoryCode) {
        return containment.getAll(territoryCode);
    }

    public Set<String> getContainers() {
        return containment.keySet();
    }

    public Relation<String, String> getTerritoryToContained() {
        return getTerritoryToContained(true);
    }

    public Relation<String, String> getTerritoryToContained(boolean allowDeprecated) {
        return allowDeprecated ? containment : containmentNonDeprecated;
    }

    public Set<String> getSkippedElements() {
        return skippedElements;
    }

    public Set<String> getZone_aliases(String zone) {
        Set<String> result = zone_aliases.getAll(zone);
        if (result == null) {
            return Collections.emptySet();
        }
        return result;
    }

    public String getZone_territory(String zone) {
        return zone_territory.get(zone);
    }

    public Set<String> getCanonicalZones() {
        return zone_territory.keySet();
    }

    /**
     * Return the multizone countries (should change name).
     * 
     * @return
     */
    public Set<String> getMultizones() {
        // TODO Auto-generated method stub
        return multizone;
    }

    private Set<String> singleRegionZones;

    public Set<String> getSingleRegionZones() {
        synchronized (this) {
            if (singleRegionZones == null) {
                singleRegionZones = new HashSet<String>();
                SupplementalDataInfo supplementalData = this; // TODO: this?
                Set<String> multizoneCountries = supplementalData.getMultizones();
                for (String zone : supplementalData.getCanonicalZones()) {
                    String region = supplementalData.getZone_territory(zone);
                    if (!multizoneCountries.contains(region) || zone.startsWith("Etc/")) {
                        singleRegionZones.add(zone);
                    }
                }
                singleRegionZones.remove("Etc/Unknown"); // remove special case
                singleRegionZones = Collections.unmodifiableSet(singleRegionZones);
            }
        }
        return singleRegionZones;
    }

    public Set<String> getTerritoriesForPopulationData(String language) {
        return languageToTerritories.getAll(language);
    }

    public Set<String> getLanguagesForTerritoriesPopulationData() {
        return languageToTerritories.keySet();
    }

    /**
     * Return the list of default content locales.
     * 
     * @return
     */
    public Set<String> getDefaultContentLocales() {
        return defaultContentLocales;
    }

    public static Map<String, String> makeLocaleToDefaultContents(Set<String> defaultContents,
        Map<String, String> result, Set<String> errors) {
        for (String s : defaultContents) {
            String simpleParent = LanguageTagParser.getSimpleParent(s);
            String oldValue = result.get(simpleParent);
            if (oldValue != null) {
                errors.add("*** Error: Default contents cannot contain two children for the same parent:\t"
                    + oldValue + ", " + s + "; keeping " + oldValue);
                continue;
            }
            result.put(simpleParent, s);
        }
        return result;
    }

    /**
     * Return the list of default content locales.
     * 
     * @return
     */
    public Set<CLDRLocale> getDefaultContentCLDRLocales() {
        initCLDRLocaleBasedData();
        return defaultContentToBase.keySet();
    }

    /**
     * Get the default content locale for a specified language
     * 
     * @param language
     *            language to search
     * @return default content, or null if none
     */
    public String getDefaultContentLocale(String language) {
        for (String dc : defaultContentLocales) {
            if (dc.startsWith(language + "_")) {
                return dc;
            }
        }
        return null;
    }

    /**
     * Get the default content locale for a specified language and script.
     * If script is null, delegates to {@link #getDefaultContentLocale(String)}
     * 
     * @param language
     * @param script
     *            if null, delegates to {@link #getDefaultContentLocale(String)}
     * @return default content, or null if none
     */
    public String getDefaultContentLocale(String language, String script) {
        if (script == null) return getDefaultContentLocale(language);
        for (String dc : defaultContentLocales) {
            if (dc.startsWith(language + "_" + script + "_")) {
                return dc;
            }
        }
        return null;
    }

    /**
     * Given a default locale (such as 'wo_Arab_SN') return the base locale (such as 'wo'), or null if the input wasn't
     * a default conetnt locale.
     * 
     * @param baseLocale
     * @return
     */
    public CLDRLocale getBaseFromDefaultContent(CLDRLocale dcLocale) {
        initCLDRLocaleBasedData();
        return defaultContentToBase.get(dcLocale);
    }

    /**
     * Given a base locale (such as 'wo') return the default content locale (such as 'wo_Arab_SN'), or null.
     * 
     * @param baseLocale
     * @return
     */
    public CLDRLocale getDefaultContentFromBase(CLDRLocale baseLocale) {
        initCLDRLocaleBasedData();
        return baseToDefaultContent.get(baseLocale);
    }

    /**
     * Is this a default content locale?
     * 
     * @param dcLocale
     * @return
     */
    public boolean isDefaultContent(CLDRLocale dcLocale) {
        initCLDRLocaleBasedData();
        if (dcLocale == null) throw new NullPointerException("null locale");
        return (defaultContentToBase.get(dcLocale) != null);
    }

    public Set<String> getNumberingSystems() {
        return numberingSystems.keySet();
    }

    public Set<String> getNumericNumberingSystems() {
        return numericSystems;
    }

    public String getDigits(String numberingSystem) {
        return numberingSystems.get(numberingSystem).digits;
    }

    public NumberingSystemType getNumberingSystemType(String numberingSystem) {
        return numberingSystems.get(numberingSystem).type;
    }

    public SortedSet<CoverageLevelInfo> getCoverageLevelInfo() {
        return coverageLevels;
    }

    /**
     * Used to get the coverage value for a path. Note, it is more efficient to create
     * a CoverageLevel2 for a language, and keep it around.
     * 
     * @param xpath
     * @param loc
     * @return
     */
    public int getCoverageValue(String xpath, ULocale loc) {
        CoverageLevel2 cov = localeToCoverageLevelInfo.get(loc);
        if (cov == null) {
            cov = CoverageLevel2.getInstance(this, loc.getBaseName());
            localeToCoverageLevelInfo.put(loc, cov);
        }

        return cov.getIntLevel(xpath);
    }

    private RegexLookup<Level> coverageLookup = null;

    public synchronized RegexLookup<Level> getCoverageLookup() {
        if (coverageLookup == null) {
            RegexLookup<Level> lookup = new RegexLookup<Level>();

            Matcher variable = Pattern.compile("\\$\\{[\\-A-Za-z]*\\}").matcher("");

            for (CoverageLevelInfo ci : getCoverageLevelInfo()) {
                String pattern = ci.match.replace('\'', '"')
                    .replace("[@", "\\[@") // make sure that attributes are quoted
                    .replace("(", "(?:") // make sure that there are no capturing groups (beyond what we generate
                // below).
                ;
                pattern = "^//ldml/" + pattern + "$"; // for now, force a complete match
                String variableType = null;
                variable.reset(pattern);
                if (variable.find()) {
                    pattern = pattern.substring(0, variable.start()) + "([^\"]*)" + pattern.substring(variable.end());
                    variableType = variable.group();
                    if (variable.find()) {
                        throw new IllegalArgumentException("We can only handle a single variable on a line");
                    }
                }

                // .replaceAll("\\]","\\\\]");
                lookup.add(new CoverageLevel2.MyRegexFinder(pattern, variableType, ci), ci.value);
            }
            coverageLookup = lookup;
        }
        return coverageLookup;
    }

    /**
     * This appears to be unused, so didn't provide new version.
     * 
     * @param xpath
     * @return
     */
    public int getCoverageValueOld(String xpath) {
        ULocale loc = new ULocale("und");
        return getCoverageValueOld(xpath, loc);
    }

    /**
     * Older version of code.
     * 
     * @param xpath
     * @param loc
     * @return
     */
    public int getCoverageValueOld(String xpath, ULocale loc) {
        String targetLanguage = loc.getLanguage();

        CoverageVariableInfo cvi = getCoverageVariableInfo(targetLanguage);
        String targetScriptString = toRegexString(cvi.targetScripts);
        String targetTerritoryString = toRegexString(cvi.targetTerritories);
        String calendarListString = toRegexString(cvi.calendars);
        String targetCurrencyString = toRegexString(cvi.targetCurrencies);
        String targetTimeZoneString = toRegexString(cvi.targetTimeZones);
        String targetPluralsString = toRegexString(cvi.targetPlurals);
        Iterator<CoverageLevelInfo> i = coverageLevels.iterator();
        while (i.hasNext()) {
            CoverageLevelInfo ci = i.next();
            String regex = "//ldml/" + ci.match.replace('\'', '"')
                .replaceAll("\\[", "\\\\[")
                .replaceAll("\\]", "\\\\]")
                .replace("${Target-Language}", targetLanguage)
                .replace("${Target-Scripts}", targetScriptString)
                .replace("${Target-Territories}", targetTerritoryString)
                .replace("${Target-TimeZones}", targetTimeZoneString)
                .replace("${Target-Currencies}", targetCurrencyString)
                .replace("${Target-Plurals}", targetPluralsString)
                .replace("${Calendar-List}", calendarListString);

            // Special logic added for coverage fields that are only to be applicable
            // to certain territories
            if (ci.inTerritory != null) {
                if (ci.inTerritory.equals("EU")) {
                    Set<String> containedTerritories = new HashSet<String>();
                    containedTerritories.addAll(getContained(ci.inTerritory));
                    containedTerritories.retainAll(cvi.targetTerritories);
                    if (containedTerritories.isEmpty()) {
                        continue;
                    }
                }
                else {
                    if (!cvi.targetTerritories.contains(ci.inTerritory)) {
                        continue;
                    }
                }
            }
            // Special logic added for coverage fields that are only to be applicable
            // to certain languages
            if (ci.inLanguage != null && !targetLanguage.matches(ci.inLanguage)) {
                continue;
            }

            // Special logic added for coverage fields that are only to be applicable
            // to certain scripts
            if (ci.inScript != null && !cvi.targetScripts.contains(ci.inScript)) {
                continue;
            }

            if (xpath.matches(regex)) {
                return ci.value.getLevel();
            }

            if (xpath.matches(regex)) {
                return ci.value.getLevel();
            }
        }
        return Level.OPTIONAL.getLevel(); // If no match then return highest possible value
    }

    public CoverageVariableInfo getCoverageVariableInfo(String targetLanguage) {
        CoverageVariableInfo cvi;
        if (localeSpecificVariables.containsKey(targetLanguage)) {
            cvi = localeSpecificVariables.get(targetLanguage);
        } else {
            cvi = new CoverageVariableInfo();
            cvi.targetScripts = getTargetScripts(targetLanguage);
            cvi.targetTerritories = getTargetTerritories(targetLanguage);
            cvi.calendars = getCalendars(cvi.targetTerritories);
            cvi.targetCurrencies = getCurrentCurrencies(cvi.targetTerritories);
            cvi.targetTimeZones = getCurrentTimeZones(cvi.targetTerritories);
            cvi.targetPlurals = getTargetPlurals(targetLanguage);
            localeSpecificVariables.put(targetLanguage, cvi);
        }
        return cvi;
    }

    private Set<String> getTargetScripts(String language) {
        Set<String> targetScripts = new HashSet<String>();
        try {
            Set<BasicLanguageData> langData = getBasicLanguageData(language);
            Iterator<BasicLanguageData> ldi = langData.iterator();
            while (ldi.hasNext()) {
                BasicLanguageData bl = ldi.next();
                Set<String> addScripts = bl.scripts;
                if (addScripts != null && bl.getType() != BasicLanguageData.Type.secondary) {
                    targetScripts.addAll(addScripts);
                }
            }
        } catch (Exception e) {
            // fall through
        }

        if (targetScripts.size() == 0) {
            targetScripts.add("Zzzz"); // Unknown Script
        }
        return targetScripts;
    }

    private Set<String> getTargetTerritories(String language) {
        Set<String> targetTerritories = new HashSet<String>();
        try {
            Set<BasicLanguageData> langData = getBasicLanguageData(language);
            Iterator<BasicLanguageData> ldi = langData.iterator();
            while (ldi.hasNext()) {
                BasicLanguageData bl = ldi.next();
                Set<String> addTerritories = bl.territories;
                if (addTerritories != null && bl.getType() != BasicLanguageData.Type.secondary) {
                    targetTerritories.addAll(addTerritories);
                }
            }
        } catch (Exception e) {
            // fall through
        }
        if (targetTerritories.size() == 0) {
            targetTerritories.add("ZZ");
        }
        return targetTerritories;
    }

    private Set<String> getCalendars(Set<String> territories) {
        Set<String> targetCalendars = new HashSet<String>();
        Iterator<String> it = territories.iterator();
        while (it.hasNext()) {
            List<String> addCalendars = getCalendars(it.next());
            if (addCalendars == null) {
                continue;
            }
            targetCalendars.addAll(addCalendars);
        }
        return targetCalendars;
    }

    /**
     * @param territory
     * @return a list the calendars used in the specified territorys
     */
    public List<String> getCalendars(String territory) {
        return calendarPreferences.get(territory);
    }

    private Set<String> getCurrentCurrencies(Set<String> territories) {
        Set<String> targetCurrencies = new HashSet<String>();
        Iterator<String> it = territories.iterator();
        Date now = new Date();
        while (it.hasNext()) {
            Set<CurrencyDateInfo> targetCurrencyInfo = getCurrencyDateInfo(it.next());
            if (targetCurrencyInfo == null) {
                continue;
            }
            Iterator<CurrencyDateInfo> it2 = targetCurrencyInfo.iterator();
            while (it2.hasNext()) {
                CurrencyDateInfo cdi = it2.next();
                if (cdi.getStart().before(now) && cdi.getEnd().after(now) && cdi.isLegalTender()) {
                    targetCurrencies.add(cdi.getCurrency());
                }
            }
        }
        return targetCurrencies;
    }

    private Set<String> getCurrentTimeZones(Set<String> territories) {
        Set<String> targetTimeZones = new HashSet<String>();
        Iterator<String> it = territories.iterator();
        while (it.hasNext()) {
            String[] countryIDs = TimeZone.getAvailableIDs(it.next());
            for (int i = 0; i < countryIDs.length; i++) {
                targetTimeZones.add(countryIDs[i]);
            }
        }
        return targetTimeZones;
    }

    private Set<String> getTargetPlurals(String language) {
        Set<String> targetPlurals = new HashSet<String>();
        targetPlurals.addAll(getPlurals(PluralType.cardinal, language).getCanonicalKeywords());
        // TODO: Kept 0 and 1 specifically until Mark figures out what to do with them.
        // They should be removed once this is done.
        targetPlurals.add("0");
        targetPlurals.add("1");
        return targetPlurals;
    }

    public String getExplicitParentLocale(String loc) {
        if (parentLocales.containsKey(loc)) {
            return parentLocales.get(loc);
        }
        return null;
    }

    /**
     * Return the canonicalized zone, or null if there is none.
     * 
     * @param alias
     * @return
     */
    public String getZoneFromAlias(String alias) {
        String zone = alias_zone.get(alias);
        if (zone != null)
            return zone;
        if (zone_territory.get(alias) != null)
            return alias;
        return null;
    }

    public boolean isCanonicalZone(String alias) {
        return zone_territory.get(alias) != null;
    }

    /**
     * Return the approximate economic weight of this language, computed by taking
     * all of the languages in each territory, looking at the literate population
     * and dividing up the GDP of the territory (in PPP) according to the
     * proportion that language has of the total. This is only an approximation,
     * since the language information is not complete, languages may overlap
     * (bilingual speakers), the literacy figures may be estimated, and literacy
     * is only a rough proxy for weight of each language in the economy of the
     * territory.
     * 
     * @param language
     * @return
     */
    public double getApproximateEconomicWeight(String targetLanguage) {
        double weight = 0;
        Set<String> territories = getTerritoriesForPopulationData(targetLanguage);
        if (territories == null) return weight;
        for (String territory : territories) {
            Set<String> languagesInTerritory = getTerritoryToLanguages(territory);
            double totalLiteratePopulation = 0;
            double targetLiteratePopulation = 0;
            for (String language : languagesInTerritory) {
                PopulationData populationData = getLanguageAndTerritoryPopulationData(
                    language, territory);
                totalLiteratePopulation += populationData.getLiteratePopulation();
                if (language.equals(targetLanguage)) {
                    targetLiteratePopulation = populationData.getLiteratePopulation();
                }
            }
            PopulationData territoryPopulationData = getPopulationDataForTerritory(territory);
            final double gdp = territoryPopulationData.getGdp();
            final double scaledGdp = gdp * targetLiteratePopulation / totalLiteratePopulation;
            if (scaledGdp > 0) {
                weight += scaledGdp;
            } else {
                // System.out.println("?\t" + territory + "\t" + targetLanguage);
            }
        }
        return weight;
    }

    public PopulationData getPopulationDataForTerritory(String territory) {
        return territoryToPopulationData.get(territory);
    }

    public Set<String> getScriptVariantsForPopulationData(String language) {
        return languageToScriptVariants.getAll(language);
    }

    public Map<String, Pair<String, String>> getReferences() {
        return references;
    }

    public Map<String, Map<String, String>> getMetazoneToRegionToZone() {
        return typeToZoneToRegionToZone.get("metazones");
    }

    public String getZoneForMetazoneByRegion(String metazone, String region) {
        String result = null;
        if (getMetazoneToRegionToZone().containsKey(metazone)) {
            Map<String, String> myMap = getMetazoneToRegionToZone().get(metazone);
            if (myMap.containsKey(region)) {
                result = myMap.get(region);
            } else {
                result = myMap.get("001");
            }
        }

        if (result == null) {
            result = "Etc/GMT";
        }

        return result;
    }

    public Map<String, Map<String, Map<String, String>>> getTypeToZoneToRegionToZone() {
        return typeToZoneToRegionToZone;
    }

    /**
     * @deprecated, use PathHeader.getMetazonePageTerritory
     */
    public Map<String, String> getMetazoneToContinentMap() {
        return metazoneContinentMap;
    }

    public Set<String> getAllMetazones() {
        return allMetazones;
    }

    public Map<String, String> getLikelySubtags() {
        return likelySubtags;
    }

    public enum PluralType {
        cardinal, ordinal
    };

    private Map<String, PluralInfo> localeToPluralInfo = new LinkedHashMap<String, PluralInfo>();
    private Map<String, PluralInfo> localeToOrdinalInfo = new LinkedHashMap<String, PluralInfo>();
    private Map<String, DayPeriodInfo> localeToDayPeriodInfo = new LinkedHashMap<String, DayPeriodInfo>();
    private Map<ULocale, CoverageLevel2> localeToCoverageLevelInfo = new LinkedHashMap<ULocale, CoverageLevel2>();
    private transient String lastPluralLocales = "root";
    private transient boolean lastPluralWasOrdinal = false;
    private transient Map<Count, String> lastPluralMap = new LinkedHashMap<Count, String>();
    private transient String lastDayPeriodLocales = null;
    private transient DayPeriodInfo.Builder dayPeriodBuilder = new DayPeriodInfo.Builder();

    private void addDayPeriodPath(XPathParts path, String value) {
        // ldml/dates/calendars/calendar[@type="gregorian"]/dayPeriods/dayPeriodContext[@type="format"]/dayPeriodWidth[@type="wide"]/dayPeriod[@type="am"]
        /*
         * <supplementalData>
         * <version number="$Revision: 8278 $"/>
         * <generation date="$Date: 2013-03-06 17:19:38 -0600 (Wed, 06 Mar 2013) $"/>
         * <dayPeriodRuleSet>
         * <dayPeriodRules locales = "en"> <!-- default for any locales not listed under other dayPeriods -->
         * <dayPeriodRule type = "am" from = "0:00" before="12:00"/>
         * <dayPeriodRule type = "pm" from = "12:00" to="24:00"/>
         */
        String locales = path.getAttributeValue(2, "locales").trim();
        if (!locales.equals(lastDayPeriodLocales)) {
            if (lastDayPeriodLocales != null) {
                addDayPeriodInfo();
            }
            lastDayPeriodLocales = locales;
        }
        DayPeriod dayPeriod = DayPeriod.valueOf(path.getAttributeValue(-1, "type"));
        String at = path.getAttributeValue(-1, "at");
        String from = path.getAttributeValue(-1, "from");
        String after = path.getAttributeValue(-1, "after");
        String to = path.getAttributeValue(-1, "to");
        String before = path.getAttributeValue(-1, "before");
        if (at != null) {
            if (from != null || after != null || to != null || before != null) {
                throw new IllegalArgumentException();
            }
            from = at;
            to = at;
        } else if ((from == null) == (after == null) || (to == null) == (before == null)) {
            throw new IllegalArgumentException();
        }
        boolean includesStart = from != null;
        boolean includesEnd = to != null;
        int start = parseTime(includesStart ? from : after);
        int end = parseTime(includesEnd ? to : before);
        // Check if any periods contain 0, e.g. 1700 - 300
        if (start > end) {
            // System.out.println("start " + start + " end " + end);
            dayPeriodBuilder.add(dayPeriod, start, includesStart, parseTime("24:00"), includesEnd);
            dayPeriodBuilder.add(dayPeriod, parseTime("0:00"), includesStart, end, includesEnd);
        } else {
            dayPeriodBuilder.add(dayPeriod, start, includesStart, end, includesEnd);
        }
    }

    static Pattern PARSE_TIME = Pattern.compile("(\\d\\d?):(\\d\\d)");

    private int parseTime(String string) {
        Matcher matcher = PARSE_TIME.matcher(string);
        if (!matcher.matches()) {
            throw new IllegalArgumentException();
        }
        return (Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2))) * 60 * 1000;
    }

    private void addDayPeriodInfo() {
        String[] locales = lastDayPeriodLocales.split("\\s+");
        DayPeriodInfo temp = dayPeriodBuilder.finish(locales);
        for (String locale : locales) {
            localeToDayPeriodInfo.put(locale, temp);
        }
    }

    private void addPluralPath(XPathParts path, String value) {
        String locales = path.getAttributeValue(2, "locales");
        String type = path.getAttributeValue(1, "type");
        boolean isOrdinal = type != null && type.equals("ordinal");
        if (!lastPluralLocales.equals(locales)) {
            addPluralInfo(isOrdinal);
            lastPluralLocales = locales;
        }
        final String countString = path.getAttributeValue(-1, "count");
        if (countString == null) return;
        Count count = Count.valueOf(countString);
        if (lastPluralMap.containsKey(count)) {
            throw new IllegalArgumentException("Duplicate plural count: " + count + " in " + locales);
        }
        lastPluralMap.put(count, value);
        lastPluralWasOrdinal = isOrdinal;
    }

    private void addPluralInfo(boolean isOrdinal) {
        final String[] locales = lastPluralLocales.split("\\s+");
        PluralInfo info = new PluralInfo(lastPluralMap);
        Map<String, PluralInfo> localeToInfo = isOrdinal ? localeToOrdinalInfo : localeToPluralInfo;
        for (String locale : locales) {
            if (localeToInfo.containsKey(locale)) {
                throw new IllegalArgumentException("Duplicate plural locale: " + locale);
            }
            localeToInfo.put(locale, info);
        }
        lastPluralMap.clear();
    }

    /**
     * Immutable class with plural info for different locales
     * 
     * @author markdavis
     */
    public static class PluralInfo {
        static final Set<Double> explicits = new HashSet<Double>();
        static {
            explicits.add(0.0d);
            explicits.add(1.0d);
        }

        public enum Count {
            zero, one, two, few, many, other;
        }

        static final Pattern pluralPaths = Pattern.compile(".*pluralRule.*");
        static final int fractDecrement = 13;
        static final int fractStart = 20;

        private final Map<Count, List<Double>> countToExampleList;
        private final Map<Count, String> countToStringExample;
        private final Map<Integer, Count> exampleToCount;
        private final PluralRules pluralRules;
        private final String pluralRulesString;
        private final Set<String> canonicalKeywords;

        private PluralInfo(Map<Count, String> countToRule) {
            // now build rules
            NumberFormat nf = NumberFormat.getNumberInstance(ULocale.ENGLISH);
            nf.setMaximumFractionDigits(2);
            StringBuilder pluralRuleBuilder = new StringBuilder();
            for (Count count : countToRule.keySet()) {
                if (pluralRuleBuilder.length() != 0) {
                    pluralRuleBuilder.append(';');
                }
                pluralRuleBuilder.append(count).append(':').append(countToRule.get(count));
            }
            pluralRulesString = pluralRuleBuilder.toString();
            pluralRules = PluralRules.createRules(pluralRulesString);

            Map<Count, List<Double>> countToExampleListRaw = new TreeMap<Count, List<Double>>();
            Map<Integer, Count> exampleToCountRaw = new TreeMap<Integer, Count>();
            Map<Count, UnicodeSet> typeToExamples2 = new TreeMap<Count, UnicodeSet>();

            for (int i = 0; i < 1000; ++i) {
                Count type = Count.valueOf(pluralRules.select(i));
                UnicodeSet uset = typeToExamples2.get(type);
                if (uset == null) typeToExamples2.put(type, uset = new UnicodeSet());
                uset.add(i);
            }
            // double check
            // if (!targetKeywords.equals(typeToExamples2.keySet())) {
            // throw new IllegalArgumentException ("Problem in plurals " + targetKeywords + ", " + this);
            // }
            // now fix the longer examples
            String otherFractionalExamples = "";
            List<Double> otherFractions = new ArrayList<Double>(0);

            // add fractional samples
            Map<Count, String> countToStringExampleRaw = new TreeMap<Count, String>();
            int fractionValue = fractStart + fractDecrement;
            for (Count type : typeToExamples2.keySet()) {
                UnicodeSet uset = typeToExamples2.get(type);
                int sample = uset.getRangeStart(0);
                if (sample == 0 && uset.size() > 1) { // pick non-zero if possible
                    UnicodeSet temp = new UnicodeSet(uset);
                    temp.remove(0);
                    sample = temp.getRangeStart(0);
                }
                Integer sampleInteger = sample;
                exampleToCountRaw.put(sampleInteger, type);

                final ArrayList<Double> arrayList = new ArrayList<Double>();
                arrayList.add((double) sample);

                // add fractional examples
                fractionValue -= fractDecrement;
                if (fractionValue < 0) {
                    fractionValue += 100;
                }
                final double fraction = (sample + (fractionValue / 100.0d));
                Count fracType = Count.valueOf(pluralRules.select(fraction));
                boolean addCurrentFractionalExample = false;

                if (fracType == Count.other) {
                    otherFractions.add(fraction);
                    if (otherFractionalExamples.length() != 0) {
                        otherFractionalExamples += ", ";
                    }
                    otherFractionalExamples += nf.format(fraction);
                } else if (fracType == type) {
                    arrayList.add(fraction);
                    addCurrentFractionalExample = true;
                } // else we ignore it

                StringBuilder b = new StringBuilder();
                int limit = uset.getRangeCount();
                int count = 0;
                boolean addEllipsis = false;
                for (int i = 0; i < limit; ++i) {
                    if (count > 5) {
                        addEllipsis = true;
                        break;
                    }
                    int start = uset.getRangeStart(i);
                    int end = uset.getRangeEnd(i);
                    if (b.length() != 0) {
                        b.append(", ");
                    }
                    if (start == end) {
                        b.append(start);
                        ++count;
                    } else if (start + 1 == end) {
                        b.append(start).append(", ").append(end);
                        count += 2;
                    } else {
                        b.append(start).append('-').append(end);
                        count += 2;
                    }
                }
                if (addCurrentFractionalExample) {
                    if (b.length() != 0) {
                        b.append(", ");
                    }
                    b.append(nf.format(fraction)).append("...");
                } else if (addEllipsis) {
                    b.append("...");
                }

                countToExampleListRaw.put(type, arrayList);
                countToStringExampleRaw.put(type, b.toString());
            }
            final String baseOtherExamples = countToStringExampleRaw.get(Count.other);
            String otherExamples = (baseOtherExamples == null ? "" : baseOtherExamples + "; ")
                + otherFractionalExamples + "...";
            countToStringExampleRaw.put(Count.other, otherExamples);
            // add otherFractions
            List<Double> list_temp = countToExampleListRaw.get(Count.other);
            if (list_temp == null) {
                countToExampleListRaw.put(Count.other, list_temp = new ArrayList<Double>(0));
            }
            list_temp.addAll(otherFractions);

            for (Count type : countToExampleListRaw.keySet()) {
                List<Double> list = countToExampleListRaw.get(type);
                // if (type.equals(Count.other)) {
                // list.addAll(otherFractions);
                // }
                list = Collections.unmodifiableList(list);
            }

            countToExampleList = Collections.unmodifiableMap(countToExampleListRaw);
            countToStringExample = Collections.unmodifiableMap(countToStringExampleRaw);
            exampleToCount = Collections.unmodifiableMap(exampleToCountRaw);
            Set<String> temp = new LinkedHashSet<String>();
            // String keyword = pluralRules.select(0.0d);
            // double value = pluralRules.getUniqueKeywordValue(keyword);
            // if (value == pluralRules.NO_UNIQUE_VALUE) {
            // temp.add("0");
            // }
            // keyword = pluralRules.select(1.0d);
            // value = pluralRules.getUniqueKeywordValue(keyword);
            // if (value == pluralRules.NO_UNIQUE_VALUE) {
            // temp.add("1");
            // }
            Set<String> keywords = pluralRules.getKeywords();
            for (Count count : Count.values()) {
                String keyword = count.toString();
                if (keywords.contains(keyword)) {
                    temp.add(keyword);
                }
            }
            // if (false) {
            // change to this after rationalizing 0/1
            // temp.add("0");
            // temp.add("1");
            // for (Count count : Count.values()) {
            // temp.add(count.toString());
            // KeywordStatus status = org.unicode.cldr.util.PluralRulesUtil.getKeywordStatus(pluralRules,
            // count.toString(), 0, explicits, true);
            // if (status != KeywordStatus.SUPPRESSED && status != KeywordStatus.INVALID) {
            // temp.add(count.toString());
            // }
            // }
            // }
            canonicalKeywords = Collections.unmodifiableSet(temp);
        }

        public String toString() {
            return countToExampleList + "; " + exampleToCount + "; " + pluralRules;
        }

        public Map<Count, List<Double>> getCountToExamplesMap() {
            return countToExampleList;
        }

        public Map<Count, String> getCountToStringExamplesMap() {
            return countToStringExample;
        }

        public Count getCount(double exampleCount) {
            return Count.valueOf(pluralRules.select(exampleCount));
        }

        public PluralRules getPluralRules() {
            return pluralRules;
        }

        public String getRules() {
            return pluralRulesString;
        }

        public Count getDefault() {
            return null;
        }

        public Set<String> getCanonicalKeywords() {
            return canonicalKeywords;
        }
    }

    /**
     * @deprecated use {@link #getPlurals(PluralType)} instead
     */
    public Set<String> getPluralLocales() {
        return getPluralLocales(PluralType.cardinal);
    }

    /**
     * @param type
     * @return the set of locales that have rules for the specified plural type
     */
    public Set<String> getPluralLocales(PluralType type) {
        return type == PluralType.cardinal ? localeToPluralInfo.keySet() : localeToOrdinalInfo.keySet();
    }

    /**
     * @deprecated use {@link #getPlurals(PluralType, String)} instead
     */
    public PluralInfo getPlurals(String locale) {
        return getPlurals(locale, true);
    }

    /**
     * Returns the plural info for a given locale.
     * 
     * @param locale
     * @return
     */
    public PluralInfo getPlurals(PluralType type, String locale) {
        return getPlurals(type, locale, true);
    }

    /**
     * @deprecated use {@link #getPlurals(PluralType, String, boolean)} instead.
     */
    public PluralInfo getPlurals(String locale, boolean allowRoot) {
        return getPlurals(PluralType.cardinal, locale, allowRoot);
    }

    /**
     * Returns the plural info for a given locale.
     * 
     * @param locale
     * @param allowRoot
     * @param type
     * @return
     */
    public PluralInfo getPlurals(PluralType type, String locale, boolean allowRoot) {
        Map<String, PluralInfo> infoMap = type == PluralType.cardinal ? localeToPluralInfo : localeToOrdinalInfo;
        while (locale != null) {
            if (!allowRoot && locale.equals("root")) {
                break;
            }
            PluralInfo result = infoMap.get(locale);
            if (result != null) return result;
            locale = LocaleIDParser.getSimpleParent(locale);
        }
        return null;
    }

    public DayPeriodInfo getDayPeriods(String locale) {
        while (locale != null) {
            DayPeriodInfo result = localeToDayPeriodInfo.get(locale);
            if (result != null) return result;
            locale = LocaleIDParser.getSimpleParent(locale);
        }
        return null;
    }

    public Set<String> getDayPeriodLocales() {
        return localeToDayPeriodInfo.keySet();
    }

    private static CurrencyNumberInfo DEFAULT_NUMBER_INFO = new CurrencyNumberInfo(2, 0, 0);

    public CurrencyNumberInfo getCurrencyNumberInfo(String currency) {
        CurrencyNumberInfo result = currencyToCurrencyNumberInfo.get(currency);
        if (result == null) {
            result = DEFAULT_NUMBER_INFO;
        }
        return result;
    }

    /**
     * Returns ordered set of currency data information
     * 
     * @param territory
     * @return
     */
    public Set<CurrencyDateInfo> getCurrencyDateInfo(String territory) {
        return territoryToCurrencyDateInfo.getAll(territory);
    }

    public Map<String, Set<TelephoneCodeInfo>> getTerritoryToTelephoneCodeInfo() {
        return territoryToTelephoneCodeInfo;
    }

    public Set<TelephoneCodeInfo> getTelephoneCodeInfoForTerritory(String territory) {
        return territoryToTelephoneCodeInfo.get(territory);
    }

    public Set<String> getTerritoriesForTelephoneCodeInfo() {
        return territoryToTelephoneCodeInfo.keySet();
    }

    private List<String> attributeOrder;
    private List<String> elementOrder;
    private List<String> serialElements;
    private Collection<String> distinguishingAttributes;

    public List<String> getAttributeOrder() {
        return attributeOrder;
    }

    public List<String> getElementOrder() {
        return elementOrder;
    }

    public List<String> getSerialElements() {
        return serialElements;
    }

    public Collection<String> getDistinguishingAttributes() {
        return distinguishingAttributes;
    }

    public List<R4<String, String, Integer, Boolean>> getLanguageMatcherData(String string) {
        return languageMatch.get(string);
    }

    /**
     * Return mapping from type to territory to data. 001 is the default.
     */
    public Map<MeasurementType, Map<String, String>> getTerritoryMeasurementData() {
        return measurementData;
    }

    /**
     * Return mapping from keys to subtypes
     */
    public Relation<String, String> getBcp47Keys() {
        return bcp47Key2Subtypes;
    }

    /**
     * Return mapping from extensions to keys
     */
    public Relation<String, String> getBcp47Extension2Keys() {
        return bcp47Extension2Keys;
    }

    /**
     * Return mapping from &lt;key,subtype> to aliases
     */
    public Relation<R2<String, String>, String> getBcp47Aliases() {
        return bcp47Aliases;
    }

    /**
     * Return mapping from &lt;key,subtype> to description
     */
    public Map<R2<String, String>, String> getBcp47Descriptions() {
        return bcp47Descriptions;
    }

    /**
     * Return mapping from &lt;key,subtype> to since
     */
    public Map<R2<String, String>, String> getBcp47Since() {
        return bcp47Since;
    }

    static Set<String> MainTimeZones;;

    /**
     * Return canonical timezones
     * 
     * @return
     */
    public Set<String> getCanonicalTimeZones() {
        synchronized (SupplementalDataInfo.class) {
            if (MainTimeZones == null) {
                MainTimeZones = new TreeSet<String>();
                SupplementalDataInfo info = SupplementalDataInfo.getInstance();
                for (Entry<R2<String, String>, Set<String>> entry : info.getBcp47Aliases().keyValuesSet()) {
                    R2<String, String> subtype_aliases = entry.getKey();
                    if (!subtype_aliases.get0().equals("timezone")) {
                        continue;
                    }
                    MainTimeZones.add(entry.getValue().iterator().next());
                }
                MainTimeZones = Collections.unmodifiableSet(MainTimeZones);
            }
            return MainTimeZones;
        }
    }

    public Set<MetaZoneRange> getMetaZoneRanges(String zone) {
        return zoneToMetaZoneRanges.get(zone);
    }

    /**
     * Return the metazone containing this zone at this date
     * 
     * @param zone
     * @param date
     * @return
     */
    public MetaZoneRange getMetaZoneRange(String zone, long date) {
        Set<MetaZoneRange> metazoneRanges = zoneToMetaZoneRanges.get(zone);
        if (metazoneRanges != null) {
            for (MetaZoneRange metazoneRange : metazoneRanges) {
                if (metazoneRange.dateRange.getFrom() <= date && date < metazoneRange.dateRange.getTo()) {
                    return metazoneRange;
                }
            }
        }
        return null;
    }

    public Map<String, Map<String, Relation<String, String>>> getDeprecationInfo() {
        return deprecated;
    }

    /**
     * returns true if the path contains a deprecated combination of element or attribute or attribute value
     * 
     * @param type
     * @param parts
     * @return
     */
    public boolean hasDeprecatedItem(String type, XPathParts parts) {
        Map<String, Relation<String, String>> badStarElements2Attributes2Values = deprecated.get(STAR);
        if (matchesBad(parts, badStarElements2Attributes2Values)) {
            return true;
        }
        Map<String, Relation<String, String>> badElements2Attributes2Values = deprecated.get(type);
        if (matchesBad(parts, badElements2Attributes2Values)) {
            return true;
        }
        return false;
    }

    private boolean matchesBad(XPathParts parts, Map<String, Relation<String, String>> badElements2Attributes2Values) {
        if (badElements2Attributes2Values == null) {
            return false;
        }
        Relation<String, String> badStarAttributes2Values = badElements2Attributes2Values.get(STAR);
        for (int i = 0; i < parts.size(); ++i) {
            Map<String, String> attributeToValue = parts.getAttributes(i);
            if (matchesBad(badStarAttributes2Values, attributeToValue)) {
                return true;
            }
            String element = parts.getElement(i);
            Relation<String, String> badAttributes2Values = badElements2Attributes2Values.get(element);
            if (matchesBad(badAttributes2Values, attributeToValue)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBad(Relation<String, String> badStarAttributes2Values, Map<String, String> attributeToValue) {
        if (badStarAttributes2Values == null) {
            return false;
        }
        Set<String> badStarValues = badStarAttributes2Values.get(STAR);
        if (badStarValues != null && badStarValues.contains(STAR)) {
            return true;
        }
        // at this point, we know that badStarValues doesn't contain STAR
        for (Entry<String, String> attributeValue : attributeToValue.entrySet()) {
            String value = attributeValue.getValue();
            if (badStarValues != null && badStarValues.contains(value)) {
                return true;
            }
            String attribute = attributeValue.getKey();
            Set<String> badValues = badStarAttributes2Values.get(attribute);
            if (badValues == null) {
                continue;
            }
            if (badValues.contains(STAR) || badValues.contains(value)) {
                return true;
            }

        }
        return false;
    }

    public Map<String, R2<String, String>> getValidityInfo() {
        return validityInfo;
    }

    public Set<String> getCLDRLanguageCodes() {
        return CLDRLanguageCodes;
    }

    public boolean isCLDRLanguageCode(String code) {
        return CLDRLanguageCodes.contains(code);
    }

    public Set<String> getCLDRScriptCodes() {
        return CLDRScriptCodes;
    }

    public boolean isCLDRScriptCode(String code) {
        return CLDRScriptCodes.contains(code);
    }

    private synchronized void initCLDRLocaleBasedData() throws InternalError {
        // This initialization depends on SDI being initialized.
        if (defaultContentToBase == null) {
            Map<CLDRLocale, CLDRLocale> p2c = new TreeMap<CLDRLocale, CLDRLocale>();
            Map<CLDRLocale, CLDRLocale> c2p = new TreeMap<CLDRLocale, CLDRLocale>();
            TreeSet<CLDRLocale> tmpAllLocales = new TreeSet<CLDRLocale>();
            // copied from SupplementalData.java - CLDRLocale based
            for (String l : defaultContentLocales) {
                CLDRLocale child = CLDRLocale.getInstance(l);
                tmpAllLocales.add(child);
            }

            for (CLDRLocale child : tmpAllLocales) {
                // Find a parent of this locale which is NOT itself also a defaultContent
                CLDRLocale nextParent = child.getParent();
                // /System.err.println(">> considering " + child + " with parent " + nextParent);
                while (nextParent != null) {
                    if (!tmpAllLocales.contains(nextParent)) { // Did we find a parent that's also not itself a
                        // defaultContent?
                        // /System.err.println(">>>> Got 1? considering " + child + " with parent " + nextParent);
                        break;
                    }
                    // /System.err.println(">>>>> considering " + child + " with parent " + nextParent);
                    nextParent = nextParent.getParent();
                }
                // parent
                if (nextParent == null) {
                    throw new InternalError("SupplementalDataInfo.defaultContentToChild(): No valid parent for "
                        + child);
                } else if (nextParent == CLDRLocale.ROOT || nextParent == CLDRLocale.getInstance("root")) {
                    throw new InternalError(
                        "SupplementalDataInfo.defaultContentToChild(): Parent is root for default content locale "
                            + child);
                } else {
                    c2p.put(child, nextParent); // wo_Arab_SN -> wo
                    CLDRLocale oldChild = p2c.get(nextParent);
                    if (oldChild != null) {
                        CLDRLocale childParent = child.getParent();
                        if (!childParent.equals(oldChild)) {
                            throw new InternalError(
                                "SupplementalData.defaultContentToChild(): defaultContent list in wrong order? Tried to map "
                                    + nextParent + " -> " + child + ", replacing " + oldChild + " (should have been "
                                    + childParent + ")");
                        }
                    }
                    p2c.put(nextParent, child); // wo -> wo_Arab_SN
                }
            }

            // done, save the hashtables..
            baseToDefaultContent = Collections.unmodifiableMap(p2c); // wo -> wo_Arab_SN
            defaultContentToBase = Collections.unmodifiableMap(c2p); // wo_Arab_SN -> wo
        }
    }

    public Map<String, PreferredAndAllowedHour> getTimeData() {
        return timeData;
    }
}
