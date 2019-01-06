package org.unicode.cldr.util;

import java.util.Arrays;
import java.util.Collections;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.draft.ScriptMetadata;
import org.unicode.cldr.draft.ScriptMetadata.Info;
import org.unicode.cldr.tool.LikelySubtags;
import org.unicode.cldr.util.RegexLookup.Finder;
import org.unicode.cldr.util.With.SimpleIterator;

import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.impl.Row;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.Transform;
import com.ibm.icu.util.Output;
import com.ibm.icu.util.ULocale;

/**
 * Provides a mechanism for dividing up LDML paths into understandable
 * categories, eg for the Survey tool.
 */
public class PathHeader implements Comparable<PathHeader> {
    /**
     * Link to a section. Commenting out the page switch for now.
     */
    public static final String SECTION_LINK = "<a " + /* "target='CLDR_ST-SECTION' "+*/"href='";
    static boolean UNIFORM_CONTINENTS = true;
    static Factory factorySingleton = null;

    /**
     * What status the survey tool should use. Can be overridden in
     * Phase.getAction()
     */
    public enum SurveyToolStatus {
        /**
         * Never show.
         */
        DEPRECATED,
        /**
         * Hide. Can be overridden in Phase.getAction()
         */
        HIDE,
        /**
         * Don't allow Change box (except TC), instead show ticket. But allow
         * votes. Can be overridden in Phase.getAction()
         */
        READ_ONLY,
        /**
         * Allow change box and votes. Can be overridden in Phase.getAction()
         */
        READ_WRITE
    }

    private static EnumNames<SectionId> SectionIdNames = new EnumNames<SectionId>();

    /**
     * The Section for a path. Don't change these without committee buy-in. The
     * 'name' may be 'Core_Data' and the toString is 'Core Data' toString gives
     * the human name
     */
    public enum SectionId {
        Core_Data("Core Data"),
        Locale_Display_Names("Locale Display Names"),
        DateTime("Date & Time"),
        Timezones,
        Numbers,
        Currencies,
        Units,
        Misc("Miscellaneous"),
        Special;

        private SectionId(String... alternateNames) {
            SectionIdNames.add(this, alternateNames);
        }

        public static SectionId forString(String name) {
            return SectionIdNames.forString(name);
        }

        public String toString() {
            return SectionIdNames.toString(this);
        }
    }

    private static EnumNames<PageId> PageIdNames = new EnumNames<PageId>();
    private static Relation<SectionId, PageId> SectionIdToPageIds = Relation.of(new TreeMap<SectionId, Set<PageId>>(),
        TreeSet.class);

    private static class SubstringOrder implements Comparable<SubstringOrder> {
        final String mainOrder;
        final int order;

        public SubstringOrder(String source) {
            int pos = source.lastIndexOf('-') + 1;
            int ordering = COUNTS.indexOf(source.substring(pos));
            // account for digits, and "some" future proofing.
            order = ordering < 0
                ? source.charAt(pos)
                : 0x10000 + ordering;
            mainOrder = source.substring(0, pos);
        }

        @Override
        public String toString() {
            return "{" + mainOrder + ", " + order + "}";
        }

        @Override
        public int compareTo(SubstringOrder other) {
            int diff = alphabeticCompare(mainOrder, other.mainOrder);
            if (diff != 0) {
                return diff;
            }
            return order - other.order;
        }
    }

    /**
     * The Page for a path (within a Section). Don't change these without
     * committee buy-in. the name is for example WAsia where toString gives
     * Western Asia
     */
    public enum PageId {
        Alphabetic_Information(SectionId.Core_Data, "Alphabetic Information"),
        Numbering_Systems(SectionId.Core_Data, "Numbering Systems"),
        Locale_Name_Patterns(SectionId.Locale_Display_Names, "Locale Name Patterns"),
        Languages(SectionId.Locale_Display_Names),
        Scripts(SectionId.Locale_Display_Names),
        Territories(SectionId.Locale_Display_Names),
        Locale_Variants(SectionId.Locale_Display_Names, "Locale Variants"),
        Keys(SectionId.Locale_Display_Names),
        Fields(SectionId.DateTime),
        Gregorian(SectionId.DateTime),
        Generic(SectionId.DateTime),
        Buddhist(SectionId.DateTime),
        Chinese(SectionId.DateTime),
        Coptic(SectionId.DateTime),
        Dangi(SectionId.DateTime),
        Ethiopic(SectionId.DateTime),
        Ethiopic_Amete_Alem(SectionId.DateTime, "Ethiopic-Amete-Alem"),
        Hebrew(SectionId.DateTime),
        Indian(SectionId.DateTime),
        Islamic(SectionId.DateTime),
        Islamic_Civil(SectionId.DateTime, "Islamic-Civil"),
        Islamic_Rgsa(SectionId.DateTime, "Islamic-Rgsa"),
        Islamic_Tbla(SectionId.DateTime, "Islamic-Tbla"),
        Islamic_Umalqura(SectionId.DateTime, "Islamic-Umalqura"),
        Japanese(SectionId.DateTime),
        Persian(SectionId.DateTime),
        ROC(SectionId.DateTime),
        Timezone_Display_Patterns(SectionId.Timezones, "Timezone Display Patterns"),
        Timezone_Cities(SectionId.Timezones, "Timezone Cities"),
        NAmerica(SectionId.Timezones, "North America"),
        SAmerica(SectionId.Timezones, "South America"),
        Africa(SectionId.Timezones),
        Europe(SectionId.Timezones),
        Russia(SectionId.Timezones),
        WAsia(SectionId.Timezones, "Western Asia"),
        CAsia(SectionId.Timezones, "Central Asia"),
        EAsia(SectionId.Timezones, "Eastern Asia"),
        SAsia(SectionId.Timezones, "Southern Asia"),
        SEAsia(SectionId.Timezones, "South-Eastern Asia"),
        Australasia(SectionId.Timezones),
        Antarctica(SectionId.Timezones),
        Oceania(SectionId.Timezones),
        UnknownT(SectionId.Timezones, "Unknown Region"),
        Overrides(SectionId.Timezones),
        Symbols(SectionId.Numbers),
        Number_Formatting_Patterns(SectionId.Numbers, "Number Formatting Patterns"),
        Compact_Decimal_Formatting(SectionId.Numbers, "Compact Decimal Formatting"),
        Measurement_Systems(SectionId.Units, "Measurement Systems"),
        Duration(SectionId.Units),
        Length(SectionId.Units),
        MassWeight(SectionId.Units, "Mass and Weight"),
        Weather(SectionId.Units),
        OtherUnits(SectionId.Units, "Other Units"),
        CompoundUnits(SectionId.Units, "Compound Units"),
        Displaying_Lists(SectionId.Misc, "Displaying Lists"),
        LinguisticElements(SectionId.Misc, "Linguistic Elements"),
        Transforms(SectionId.Misc),
        Identity(SectionId.Special),
        Version(SectionId.Special),
        Suppress(SectionId.Special),
        Deprecated(SectionId.Special),
        Unknown(SectionId.Special),
        C_NAmerica(SectionId.Currencies, "North America (C)"), //need to add (C) to differentiate from Timezone territories
        C_SAmerica(SectionId.Currencies, "South America (C)"),
        C_Europe(SectionId.Currencies, "Europe (C)"),
        C_NWAfrica(SectionId.Currencies, "Northern/Western Africa (C)"),
        C_SEAfrica(SectionId.Currencies, "Southern/Eastern Africa (C)"),
        C_WCAsia(SectionId.Currencies, "Western/Central Asia (C)"),
        C_SEAsia(SectionId.Currencies, "Eastern/Southern Asia (C)"),
        C_Oceania(SectionId.Currencies, "Oceania (C)"),
        C_Unknown(SectionId.Currencies, "Unknown Region (C)"), ;

        private final SectionId sectionId;

        private PageId(SectionId sectionId, String... alternateNames) {
            this.sectionId = sectionId;
            SectionIdToPageIds.put(sectionId, this);
            PageIdNames.add(this, alternateNames);
        }

        /**
         * Construct a pageId given a string
         * 
         * @param name
         * @return
         */
        public static PageId forString(String name) {
            return PageIdNames.forString(name);
        }

        /**
         * Returns the page id
         * 
         * @return a page ID, such as 'Languages'
         */
        public String toString() {
            return PageIdNames.toString(this);
        }

        /**
         * Get the containing section id, such as 'Code Lists'
         * 
         * @return the containing section ID
         */
        public SectionId getSectionId() {
            return sectionId;
        }
    }

    private final SectionId sectionId;
    private final PageId pageId;
    private final String header;
    private final String code;
    private final String originalPath;
    private final SurveyToolStatus status;

    // Used for ordering
    private final int headerOrder;
    private final int codeOrder;
    private final SubstringOrder codeSuborder;

    static final Pattern SEMI = Pattern.compile("\\s*;\\s*");
    static final Matcher ALT_MATCHER = Pattern.compile(
        "\\[@alt=\"([^\"]*+)\"]")
        .matcher("");

    static final RuleBasedCollator alphabetic = (RuleBasedCollator) Collator
        .getInstance(ULocale.ENGLISH);
    static {
        alphabetic.setNumericCollation(true);
    }
    static final SupplementalDataInfo supplementalDataInfo = SupplementalDataInfo.getInstance();
    static final Map<String, String> metazoneToContinent = supplementalDataInfo
        .getMetazoneToContinentMap();
    static final StandardCodes standardCode = StandardCodes.make();
    static final Map<String, String> metazoneToPageTerritory = new HashMap<String, String>();
    static {
        Map<String, Map<String, String>> metazoneToRegionToZone = supplementalDataInfo.getMetazoneToRegionToZone();
        for (Entry<String, Map<String, String>> metazoneEntry : metazoneToRegionToZone.entrySet()) {
            String metazone = metazoneEntry.getKey();
            String worldZone = metazoneEntry.getValue().get("001");
            String territory = Containment.getRegionFromZone(worldZone);
            if (territory == null) {
                territory = "ZZ";
            }
            // Russia, Antarctica => territory
            // in Australasia, Asia, S. America => subcontinent
            // in N. America => N. America (grouping of 3 subcontinents)
            // in everything else => continent
            if (territory.equals("RU") || territory.equals("AQ")) {
                metazoneToPageTerritory.put(metazone, territory);
            } else {
                String continent = Containment.getContinent(territory);
                String subcontinent = Containment.getSubcontinent(territory);
                if (continent.equals("142")) { // Asia
                    metazoneToPageTerritory.put(metazone, subcontinent);
                } else if (continent.equals("019")) { // Americas
                    metazoneToPageTerritory.put(metazone, subcontinent.equals("005") ? subcontinent : "003");
                } else if (subcontinent.equals("053")) { // Australasia
                    metazoneToPageTerritory.put(metazone, subcontinent);
                } else {
                    metazoneToPageTerritory.put(metazone, continent);
                }
            }
        }
    }

    /**
     * @param section
     * @param sectionOrder
     * @param page
     * @param pageOrder
     * @param header
     * @param headerOrder
     * @param code
     * @param codeOrder
     * @param suborder
     * @param status
     */
    private PathHeader(SectionId sectionId, PageId pageId, String header,
        int headerOrder, String code, int codeOrder, SubstringOrder suborder, SurveyToolStatus status,
        String originalPath) {
        this.sectionId = sectionId;
        this.pageId = pageId;
        this.header = header;
        this.headerOrder = headerOrder;
        this.code = code;
        this.codeOrder = codeOrder;
        this.codeSuborder = suborder;
        this.originalPath = originalPath;
        this.status = status;
    }

    /**
     * Return a factory for use in creating the headers. This should be cached.
     * The calls are thread-safe. The englishFile sets a static for now; after
     * the first time, null can be passed.
     * 
     * @param englishFile
     */
    public static Factory getFactory(CLDRFile englishFile) {
        if (factorySingleton == null) {
            if (englishFile == null) {
                throw new IllegalArgumentException("English CLDRFile must not be null");
            }
            factorySingleton = new Factory(englishFile);
        }
        return factorySingleton;
    }

    /**
     * @deprecated
     */
    public String getSection() {
        return sectionId.toString();
    }

    public SectionId getSectionId() {
        return sectionId;
    }

    /**
     * @deprecated
     */
    public String getPage() {
        return pageId.toString();
    }

    public PageId getPageId() {
        return pageId;
    }

    public String getHeader() {
        return header;
    }

    public String getCode() {
        return code;
    }

    public String getHeaderCode() {
        return getHeader() + ": " + getCode();
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public SurveyToolStatus getSurveyToolStatus() {
        return status;
    }

    @Override
    public String toString() {
        return sectionId
            + "\t" + pageId
            + "\t" + header // + "\t" + headerOrder
            + "\t" + code // + "\t" + codeOrder
        ;
    }

    @Override
    public int compareTo(PathHeader other) {
        // Within each section, order alphabetically if the integer orders are
        // not different.
        try {
            int result;
            if (0 != (result = sectionId.compareTo(other.sectionId))) {
                return result;
            }
            if (0 != (result = pageId.compareTo(other.pageId))) {
                return result;
            }
            if (0 != (result = headerOrder - other.headerOrder)) {
                return result;
            }
            if (0 != (result = alphabeticCompare(header, other.header))) {
                return result;
            }
            if (0 != (result = codeOrder - other.codeOrder)) {
                return result;
            }
            if (codeSuborder != null) { // do all three cases, for transitivity
                if (other.codeSuborder != null) {
                    if (0 != (result = codeSuborder.compareTo(other.codeSuborder))) {
                        return result;
                    }
                } else {
                    return 1; // if codeSuborder != null (and other.codeSuborder
                    // == null), it is greater
                }
            } else if (other.codeSuborder != null) {
                return -1; // if codeSuborder == null (and other.codeSuborder !=
                // null), it is greater
            }
            if (0 != (result = alphabeticCompare(code, other.code))) {
                return result;
            }
            if (0 != (result = alphabeticCompare(originalPath, other.originalPath))) {
                return result;
            }
            return 0;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Internal problem comparing " + this + " and " + other, e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        PathHeader other;
        try {
            other = (PathHeader) obj;
        } catch (Exception e) {
            return false;
        }
        return sectionId == other.sectionId && pageId == other.pageId
            && header.equals(other.header) && code.equals(other.code)
            && originalPath.equals(other.originalPath);
    }

    @Override
    public int hashCode() {
        return originalPath.hashCode();
    }

    public static class Factory implements Transform<String, PathHeader> {
        static final RegexLookup<RawData> lookup = RegexLookup
            .of(new PathHeaderTransform())
            .setPatternTransform(
                RegexLookup.RegexFinderTransformPath)
            .loadFromFile(
                PathHeader.class,
                "data/PathHeader.txt");
        // synchronized with lookup
        static final Output<String[]> args = new Output<String[]>();
        // synchronized with lookup
        static final Counter<RawData> counter = new Counter<RawData>();
        // synchronized with lookup
        static final Map<RawData, String> samples = new HashMap<RawData, String>();
        // synchronized with lookup
        static int order;
        static SubstringOrder suborder;

        static final Map<String, PathHeader> cache = new HashMap<String, PathHeader>();
        // synchronized with cache
        static final Map<SectionId, Map<PageId, SectionPage>> sectionToPageToSectionPage = new EnumMap<SectionId, Map<PageId, SectionPage>>(
            SectionId.class);
        static final Relation<SectionPage, String> sectionPageToPaths = Relation
            .of(new TreeMap<SectionPage, Set<String>>(),
                HashSet.class);
        private static CLDRFile englishFile;
        private Set<String> matchersFound = new HashSet<String>();

        /**
         * Create a factory for creating PathHeaders.
         * 
         * @param englishFile
         *            - only sets the file (statically!) if not already set.
         */
        private Factory(CLDRFile englishFile) {
            setEnglishCLDRFileIfNotSet(englishFile); // temporary
        }

        /**
         * Returns true if we set it, false if set before.
         * 
         * @param englishFile2
         * @return
         */
        private static boolean setEnglishCLDRFileIfNotSet(CLDRFile englishFile2) {
            synchronized (Factory.class) {
                if (englishFile != null) {
                    return false;
                }
                englishFile = englishFile2;
                return true;
            }
        }

        /**
         * Use only when trying to find unmatched patterns
         */
        public void clearCache() {
            synchronized (cache) {
                cache.clear();
            }
        }

        /**
         * Return the PathHeader for a given path. Thread-safe.
         */
        public PathHeader fromPath(String path) {
            return fromPath(path, null);
        }

        /**
         * Return the PathHeader for a given path. Thread-safe.
         */
        public PathHeader transform(String path) {
            return fromPath(path, null);
        }

        /**
         * Return the PathHeader for a given path. Thread-safe.
         * @param failures a list of failures to add to.
         */
        public PathHeader fromPath(String path, List<String> failures) {
            if (path == null) {
                throw new NullPointerException("Path cannot be null");
            }
            synchronized (cache) {
                PathHeader old = cache.get(path);
                if (old != null) {
                    return old;
                }
            }
            synchronized (lookup) {
                String cleanPath = path;
                // special handling for alt
                String alt = null;
                int altPos = cleanPath.indexOf("[@alt=");
                if (altPos >= 0 && !cleanPath.endsWith("/symbol[@alt=\"narrow\"]")) {
                    if (ALT_MATCHER.reset(cleanPath).find()) {
                        alt = ALT_MATCHER.group(1);
                        cleanPath = cleanPath.substring(0, ALT_MATCHER.start())
                            + cleanPath.substring(ALT_MATCHER.end());
                        int pos = alt.indexOf("proposed");
                        if (pos >= 0) {
                            alt = pos == 0 ? null : alt.substring(0, pos - 1);
                            // drop "proposed",
                            // change "xxx-proposed" to xxx.
                        }
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
                Output<Finder> matcherFound = new Output<Finder>();
                RawData data = lookup.get(cleanPath, null, args, matcherFound, failures);
                if (data == null) {
                    return null;
                }
                matchersFound.add(matcherFound.value.toString());
                counter.add(data, 1);
                if (!samples.containsKey(data)) {
                    samples.put(data, cleanPath);
                }
                try {
                    PathHeader result = new PathHeader(
                        SectionId.forString(fix(data.section, 0)),
                        PageId.forString(fix(data.page, 0)),
                        fix(data.header, data.headerOrder),
                        order, // only valid after call to fix. TODO, make
                        // this cleaner
                        fix(data.code + (alt == null ? "" : "-" + alt), data.codeOrder),
                        order, // only valid after call to fix
                        suborder,
                        data.status,
                        path);
                    synchronized (cache) {
                        PathHeader old = cache.get(path);
                        if (old == null) {
                            cache.put(path, result);
                        } else {
                            result = old;
                        }
                        Map<PageId, SectionPage> pageToPathHeaders = sectionToPageToSectionPage
                            .get(result.sectionId);
                        if (pageToPathHeaders == null) {
                            sectionToPageToSectionPage.put(result.sectionId, pageToPathHeaders
                                = new EnumMap<PageId, SectionPage>(PageId.class));
                        }
                        SectionPage sectionPage = pageToPathHeaders.get(result.pageId);
                        if (sectionPage == null) {
                            pageToPathHeaders.put(result.pageId, sectionPage
                                = new SectionPage(result.sectionId,
                                    result.pageId));
                        }
                        sectionPageToPaths.put(sectionPage, path);
                    }
                    return result;
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Probably mismatch in Page/Section enum, or too few capturing groups in regex for " + cleanPath,
                        e);
                }
            }
        }

        private static class SectionPage implements Comparable<SectionPage> {
            private final SectionId sectionId;
            private final PageId pageId;

            public SectionPage(SectionId sectionId, PageId pageId) {
                this.sectionId = sectionId;
                this.pageId = pageId;
            }

            @Override
            public int compareTo(SectionPage other) {
                // Within each section, order alphabetically if the integer
                // orders are
                // not different.
                int result;
                if (0 != (result = sectionId.compareTo(other.sectionId))) {
                    return result;
                }
                if (0 != (result = pageId.compareTo(other.pageId))) {
                    return result;
                }
                return 0;
            }

            @Override
            public boolean equals(Object obj) {
                PathHeader other;
                try {
                    other = (PathHeader) obj;
                } catch (Exception e) {
                    return false;
                }
                return sectionId == other.sectionId && pageId == other.pageId;
            }

            @Override
            public int hashCode() {
                return sectionId.hashCode() ^ pageId.hashCode();
            }
        }

        /**
         * Returns a set of paths currently associated with the given section
         * and page.
         * <p>
         * <b>Warning:</b>
         * <ol>
         * <li>The set may not be complete for a cldrFile unless all of paths in
         * the file have had fromPath called. And this includes getExtraPaths().
         * </li>
         * <li>The set may include paths that have no value in the current
         * cldrFile.</li>
         * <li>The set may be empty, if the section/page aren't valid.</li>
         * </ol>
         * Thread-safe.
         * 
         * @target a collection where the paths are to be returned.
         */
        public static Set<String> getCachedPaths(SectionId sectionId, PageId page) {
            Set<String> target = new HashSet<String>();
            synchronized (cache) {
                Map<PageId, SectionPage> pageToSectionPage = sectionToPageToSectionPage
                    .get(sectionId);
                if (pageToSectionPage == null) {
                    return target;
                }
                SectionPage sectionPage = pageToSectionPage.get(page);
                if (sectionPage == null) {
                    return target;
                }
                Set<String> set = sectionPageToPaths.getAll(sectionPage);
                target.addAll(set);
            }
            return target;
        }

        /**
         * Return the Sections and Pages that are in defined, for display in
         * menus. Both are ordered.
         */
        public static Relation<SectionId, PageId> getSectionIdsToPageIds() {
            SectionIdToPageIds.freeze(); // just in case
            return SectionIdToPageIds;
        }

        /**
         * Return paths that have the designated section and page.
         * 
         * @param sectionId
         * @param pageId
         * @param file
         */
        public Iterable<String> filterCldr(SectionId sectionId, PageId pageId, CLDRFile file) {
            return new FilteredIterable(sectionId, pageId, file);
        }

        /**
         * Return the names for Sections and Pages that are defined, for display
         * in menus. Both are ordered.
         * 
         * @deprecated Use getSectionIdsToPageIds
         */
        public static LinkedHashMap<String, Set<String>> getSectionsToPages() {
            LinkedHashMap<String, Set<String>> sectionsToPages = new LinkedHashMap<String, Set<String>>();
            for (PageId pageId : PageId.values()) {
                String sectionId2 = pageId.getSectionId().toString();
                Set<String> pages = sectionsToPages.get(sectionId2);
                if (pages == null) {
                    sectionsToPages.put(sectionId2, pages = new LinkedHashSet<String>());
                }
                pages.add(pageId.toString());
            }
            return sectionsToPages;
        }

        /**
         * @deprecated, use the filterCldr with the section/page ids.
         */
        public Iterable<String> filterCldr(String section, String page, CLDRFile file) {
            return new FilteredIterable(section, page, file);
        }

        private class FilteredIterable implements Iterable<String>, SimpleIterator<String> {
            private final SectionId sectionId;
            private final PageId pageId;
            private final Iterator<String> fileIterator;

            FilteredIterable(SectionId sectionId, PageId pageId, CLDRFile file) {
                this.sectionId = sectionId;
                this.pageId = pageId;
                this.fileIterator = file.fullIterable().iterator();
            }

            public FilteredIterable(String section, String page, CLDRFile file) {
                this(SectionId.forString(section), PageId.forString(page), file);
            }

            @Override
            public Iterator<String> iterator() {
                return With.toIterator(this);
            }

            @Override
            public String next() {
                while (fileIterator.hasNext()) {
                    String path = fileIterator.next();
                    PathHeader pathHeader = fromPath(path);
                    if (sectionId == pathHeader.sectionId && pageId == pathHeader.pageId) {
                        return path;
                    }
                }
                return null;
            }
        }

        private static class ChronologicalOrder {
            private Map<String, Integer> map = new HashMap<String, Integer>();
            private String item;
            private int order;
            private ChronologicalOrder toClear;

            ChronologicalOrder(ChronologicalOrder toClear) {
                this.toClear = toClear;
            }

            int getOrder() {
                return order;
            }

            public String set(String itemToOrder) {
                if (itemToOrder.startsWith("*")) {
                    item = itemToOrder.substring(1, itemToOrder.length());
                    return item; // keep old order
                }
                item = itemToOrder;
                Integer old = map.get(item);
                if (old != null) {
                    order = old.intValue();
                } else {
                    order = map.size();
                    map.put(item, order);
                    clearLower();
                }
                return item;
            }

            private void clearLower() {
                if (toClear != null) {
                    toClear.map.clear();
                    toClear.order = 0;
                    toClear.clearLower();
                }
            }
        }

        static class RawData {
            static ChronologicalOrder codeOrdering = new ChronologicalOrder(null);
            static ChronologicalOrder headerOrdering = new ChronologicalOrder(codeOrdering);

            public RawData(String source) {
                String[] split = SEMI.split(source);
                section = split[0];
                // HACK
                if (section.equals("Timezones") && split[1].equals("Indian")) {
                    page = "Indian2";
                } else {
                    page = split[1];
                }

                header = headerOrdering.set(split[2]);
                headerOrder = headerOrdering.getOrder();

                code = codeOrdering.set(split[3]);
                codeOrder = codeOrdering.getOrder();

                status = split.length < 5 ? SurveyToolStatus.READ_WRITE : SurveyToolStatus.valueOf(split[4]);
            }

            public final String section;
            public final String page;
            public final String header;
            public final int headerOrder;
            public final String code;
            public final int codeOrder;
            public final SurveyToolStatus status;

            @Override
            public String toString() {
                return section + "\t"
                    + page + "\t"
                    + header + "\t" + headerOrder + "\t"
                    + code + "\t" + codeOrder + "\t"
                    + status;
            }
        }

        static class PathHeaderTransform implements Transform<String, RawData> {
            @Override
            public RawData transform(String source) {
                return new RawData(source);
            }
        }

        /**
         * Internal data, for testing and debugging.
         * 
         * @deprecated
         */
        public class CounterData extends Row.R4<String, RawData, String, String> {
            public CounterData(String a, RawData b, String c) {
                super(a, b, c == null ? "no sample" : c, c == null ? "no sample" : fromPath(c)
                    .toString());
            }
        }

        /**
         * Get the internal data, for testing and debugging.
         * 
         * @deprecated
         */
        public Counter<CounterData> getInternalCounter() {
            synchronized (lookup) {
                Counter<CounterData> result = new Counter<CounterData>();
                for (Map.Entry<Finder, RawData> foo : lookup) {
                    Finder finder = foo.getKey();
                    RawData data = foo.getValue();
                    long count = counter.get(data);
                    result.add(new CounterData(finder.toString(), data, samples.get(data)), count);
                }
                return result;
            }
        }

        static Map<String, Transform<String, String>> functionMap = new HashMap<String, Transform<String, String>>();
        static String[] months = { "Jan", "Feb", "Mar",
            "Apr", "May", "Jun",
            "Jul", "Aug", "Sep",
            "Oct", "Nov", "Dec",
            "Und" };
        static List<String> days = Arrays.asList("sun", "mon",
            "tue", "wed", "thu",
            "fri", "sat");
        // static Map<String, String> likelySubtags =
        // supplementalDataInfo.getLikelySubtags();
        static LikelySubtags likelySubtags = new LikelySubtags();
        static HyphenSplitter hyphenSplitter = new HyphenSplitter();
        static Transform<String, String> catFromTerritory;
        static Transform<String, String> catFromTimezone;
        static {
            // Put any new functions used in PathHeader.txt in here.
            // To change the order of items within a section or heading, set
            // order/suborder to be the relative position of the current item.
            functionMap.put("month", new Transform<String, String>() {
                public String transform(String source) {
                    int m = Integer.parseInt(source);
                    order = m;
                    return months[m - 1];
                }
            });
            functionMap.put("count", new Transform<String, String>() {
                public String transform(String source) {
                    suborder = new SubstringOrder(source);
                    return source;
                }
            });
            functionMap.put("count2", new Transform<String, String>() {
                public String transform(String source) {
                    int pos = source.indexOf('-');
                    source = pos + source.substring(pos);
                    suborder = new SubstringOrder(source); // make 10000-...
                    // into 5-
                    return source;
                }
            });
            functionMap.put("unitCount", new Transform<String, String>() {
                public String transform(String source) {
                    String[] unitLengths = { "long", "short", "narrow" };
                    int pos = 9;
                    for (int i = 0; i < unitLengths.length; i++) {
                        if (source.startsWith(unitLengths[i])) {
                            pos = i;
                            continue;
                        }
                    }
                    suborder = new SubstringOrder(pos + "-" + source); //
                    return source;
                }
            });
            functionMap.put("day", new Transform<String, String>() {
                public String transform(String source) {
                    int m = days.indexOf(source);
                    order = m;
                    return source;
                }
            });
            functionMap.put("calendar", new Transform<String, String>() {
                Map<String, String> fixNames = Builder.with(new HashMap<String, String>())
                    .put("islamicc", "Islamic Civil")
                    .put("roc", "ROC")
                    .put("Ethioaa", "Ethiopic Amete Alem")
                    .put("Gregory", "Gregorian")
                    .put("iso8601", "ISO 8601")
                    .freeze();

                public String transform(String source) {
                    String result = fixNames.get(source);
                    return result != null ? result : UCharacter.toTitleCase(source, null);
                }
            });

            functionMap.put("calField", new Transform<String, String>() {
                public String transform(String source) {
                    String[] fields = source.split(":", 3);
                    order = 0;
                    final List<String> widthValues = Arrays.asList(
                        "wide", "abbreviated", "short", "narrow");
                    final List<String> calendarFieldValues = Arrays.asList(
                        "Eras",
                        "Quarters",
                        "Months",
                        "Days",
                        "DayPeriods",
                        "Formats");
                    final List<String> calendarFormatTypes = Arrays.asList(
                        "Standard",
                        "Flexible",
                        "Intervals");
                    final List<String> calendarContextTypes = Arrays.asList(
                        "none",
                        "format",
                        "stand-alone");
                    final List<String> calendarFormatSubtypes = Arrays.asList(
                        "date",
                        "time",
                        "dateTime",
                        "fallback");

                    Map<String, String> fixNames = Builder.with(new HashMap<String, String>())
                        .put("DayPeriods", "Day Periods")
                        .put("format", "Formatting Context")
                        .put("stand-alone", "Standalone Context")
                        .put("none", "")
                        .put("date", "Date Formats")
                        .put("time", "Time Formats")
                        .put("dateTime", "Date & Time Combination Formats")
                        .freeze();

                    if (calendarFieldValues.contains(fields[0])) {
                        order = calendarFieldValues.indexOf(fields[0]) * 100;
                    } else {
                        order = calendarFieldValues.size() * 100;
                    }

                    if (fields[0].equals("Formats")) {
                        if (calendarFormatTypes.contains(fields[1])) {
                            order += calendarFormatTypes.indexOf(fields[1]) * 10;
                        } else {
                            order += calendarFormatTypes.size() * 10;
                        }
                        if (calendarFormatSubtypes.contains(fields[2])) {
                            order += calendarFormatSubtypes.indexOf(fields[2]);
                        } else {
                            order += calendarFormatSubtypes.size();
                        }
                    } else {
                        if (widthValues.contains(fields[1])) {
                            order += widthValues.indexOf(fields[1]) * 10;
                        } else {
                            order += widthValues.size() * 10;
                        }
                        if (calendarContextTypes.contains(fields[2])) {
                            order += calendarContextTypes.indexOf(fields[2]);
                        } else {
                            order += calendarContextTypes.size();
                        }
                    }

                    String[] fixedFields = new String[fields.length];
                    for (int i = 0; i < fields.length; i++) {
                        String s = fixNames.get(fields[i]);
                        fixedFields[i] = s != null ? s : fields[i];
                    }

                    return fixedFields[0] +
                        " - " + fixedFields[1] +
                        (fixedFields[2].length() > 0 ? " - " + fixedFields[2] : "");
                }
            });

            functionMap.put("titlecase", new Transform<String, String>() {
                public String transform(String source) {
                    return UCharacter.toTitleCase(source, null);
                }
            });
            functionMap.put("categoryFromScript", new Transform<String, String>() {
                public String transform(String source) {
                    String script = hyphenSplitter.split(source);
                    Info info = ScriptMetadata.getInfo(script);
                    if (info == null) {
                        info = ScriptMetadata.getInfo("Zzzz");
                    }
                    order = 100 - info.idUsage.ordinal();
                    return info.idUsage.name;
                }
            });
            functionMap.put("scriptFromLanguage", new Transform<String, String>() {
                public String transform(String source0) {
                    String language = hyphenSplitter.split(source0);
                    String script = likelySubtags.getLikelyScript(language);
                    if (script == null) {
                        script = likelySubtags.getLikelyScript(language);
                    }
                    String scriptName = englishFile.getName(CLDRFile.SCRIPT_NAME, script);
                    return "Languages Using " + (script.equals("Hans") || script.equals("Hant") ? "Han Script"
                        : scriptName.endsWith(" Script") ? scriptName
                            : scriptName + " Script");
                }
            });
            functionMap.put("categoryFromTerritory",
                catFromTerritory = new Transform<String, String>() {
                    public String transform(String source) {
                        String territory = hyphenSplitter.split(source);
                        String container = Containment.getContainer(territory);
                        order = Containment.getOrder(territory);
                        return englishFile.getName(CLDRFile.TERRITORY_NAME, container);
                    }
                });
            functionMap.put("categoryFromTimezone",
                catFromTimezone = new Transform<String, String>() {
                    public String transform(String source0) {
                        String territory = Containment.getRegionFromZone(source0);
                        if (territory == null) {
                            territory = "ZZ";
                        }
                        return catFromTerritory.transform(territory);
                    }
                });
            functionMap.put("timezoneSorting", new Transform<String, String>() {
                public String transform(String source) {
                    final List<String> codeValues = Arrays.asList(
                        "generic-long",
                        "generic-short",
                        "standard-long",
                        "standard-short",
                        "daylight-long",
                        "daylight-short");
                    if (codeValues.contains(source)) {
                        order = codeValues.indexOf(source);
                    } else {
                        order = codeValues.size();
                    }
                    return source;
                }
            });

            functionMap.put("tzdpField", new Transform<String, String>() {
                public String transform(String source) {
                    Map<String, String> fieldNames = Builder.with(new HashMap<String, String>())
                        .put("regionFormat", "Region Format - Generic")
                        .put("regionFormat-standard", "Region Format - Standard")
                        .put("regionFormat-daylight", "Region Format - Daylight")
                        .put("gmtFormat", "GMT Format")
                        .put("hourFormat", "GMT Hours/Minutes Format")
                        .put("gmtZeroFormat", "GMT Zero Format")
                        .put("fallbackFormat", "Location Fallback Format")
                        .freeze();
                    final List<String> fieldOrder = Arrays.asList(
                        "regionFormat",
                        "regionFormat-standard",
                        "regionFormat-daylight",
                        "gmtFormat",
                        "hourFormat",
                        "gmtZeroFormat",
                        "fallbackFormat");

                    if (fieldOrder.contains(source)) {
                        order = fieldOrder.indexOf(source);
                    } else {
                        order = fieldOrder.size();
                    }

                    String result = fieldNames.get(source);
                    return result == null ? source : result;
                }
            });

            functionMap.put("numericSort", new Transform<String, String>() {
                // Probably only works well for small values, like -5 through +4.
                public String transform(String source) {
                    Integer pos = Integer.valueOf(source) + 5;
                    suborder = new SubstringOrder(pos.toString());
                    return source;
                }
            });

            functionMap.put("metazone", new Transform<String, String>() {

                public String transform(String source) {
                    if (PathHeader.UNIFORM_CONTINENTS) {
                        String container = getMetazonePageTerritory(source);
                        order = Containment.getOrder(container);
                        return englishFile.getName(CLDRFile.TERRITORY_NAME, container);
                    } else {
                        String continent = metazoneToContinent.get(source);
                        if (continent == null) {
                            continent = "UnknownT";
                        }
                        return continent;
                    }
                }
            });

            Object[][] ctto = {
                { "BUK", "MM" },
                { "CSD", "RS" },
                { "CSK", "CZ" },
                { "DDM", "DE" },
                { "EUR", "EU" },
                { "RHD", "ZW" },
                { "SUR", "RU" },
                { "TPE", "TL" },
                { "XAG", "ZZ" },
                { "XAU", "ZZ" },
                { "XBA", "ZZ" },
                { "XBB", "ZZ" },
                { "XBC", "ZZ" },
                { "XBD", "ZZ" },
                { "XDR", "ZZ" },
                { "XEU", "EU" },
                { "XFO", "ZZ" },
                { "XFU", "ZZ" },
                { "XPD", "ZZ" },
                { "XPT", "ZZ" },
                { "XRE", "ZZ" },
                { "XSU", "ZZ" },
                { "XTS", "ZZ" },
                { "XUA", "ZZ" },
                { "XXX", "ZZ" },
                { "YDD", "YE" },
                { "YUD", "RS" },
                { "YUM", "RS" },
                { "YUN", "RS" },
                { "YUR", "RS" },
                { "ZRN", "CD" },
                { "ZRZ", "CD" },
            };

            Object[][] sctc = {
                { "Northern America", "North America (C)" },
                { "Central America", "North America (C)" },
                { "Caribbean", "North America (C)" },
                { "South America", "South America (C)" },
                { "Northern Africa", "Northern/Western Africa (C)" },
                { "Western Africa", "Northern/Western Africa (C)" },
                { "Middle Africa", "Northern/Western Africa (C)" },
                { "Eastern Africa", "Southern/Eastern Africa (C)" },
                { "Southern Africa", "Southern/Eastern Africa (C)" },
                { "Europe", "Europe (C)" },
                { "Northern Europe", "Europe (C)" },
                { "Western Europe", "Europe (C)" },
                { "Eastern Europe", "Europe (C)" },
                { "Southern Europe", "Europe (C)" },
                { "Western Asia", "Western/Central Asia (C)" },
                { "Central Asia", "Western/Central Asia (C)" },
                { "Eastern Asia", "Eastern/Southern Asia (C)" },
                { "Southern Asia", "Eastern/Southern Asia (C)" },
                { "South-Eastern Asia", "Eastern/Southern Asia (C)" },
                { "Australasia", "Oceania (C)" },
                { "Melanesia", "Oceania (C)" },
                { "Polynesia", "Oceania (C)" },
                { "Unknown Region", "Unknown Region (C)" },
            };

            final Map<String, String> currencyToTerritoryOverrides = CldrUtility.asMap(ctto);
            final Map<String, String> subContinentToContinent = CldrUtility.asMap(sctc);
            // TODO: Put this into supplementalDataInfo ?

            functionMap.put("categoryFromCurrency", new Transform<String, String>() {
                public String transform(String source0) {
                    String tenderOrNot = "";
                    String territory = likelySubtags.getLikelyTerritoryFromCurrency(source0);
                    if (territory == null) {
                        tenderOrNot = ": " + source0 + " (Not Current Tender)";
                    }
                    if (currencyToTerritoryOverrides.keySet().contains(source0)) {
                        territory = currencyToTerritoryOverrides.get(source0);
                    } else if (territory == null) {
                        territory = source0.substring(0, 2);
                    }

                    if (territory.equals("ZZ")) {
                        order = 999;
                        return englishFile.getName(CLDRFile.TERRITORY_NAME, territory) + ": " + source0;
                    } else {
                        return catFromTerritory.transform(territory) + ": "
                            + englishFile.getName(CLDRFile.TERRITORY_NAME, territory)
                            + tenderOrNot;
                    }
                }
            });
            functionMap.put("continentFromCurrency", new Transform<String, String>() {
                public String transform(String source0) {
                    String subContinent;
                    String territory = likelySubtags.getLikelyTerritoryFromCurrency(source0);
                    if (currencyToTerritoryOverrides.keySet().contains(source0)) {
                        territory = currencyToTerritoryOverrides.get(source0);
                    } else if (territory == null) {
                        territory = source0.substring(0, 2);
                    }

                    if (territory.equals("ZZ")) {
                        order = 999;
                        subContinent = englishFile.getName(CLDRFile.TERRITORY_NAME, territory);
                    } else {
                        subContinent = catFromTerritory.transform(territory);
                    }

                    return subContinentToContinent.get(subContinent); //the continent is the last word in the territory representation
                }
            });
            functionMap.put("numberingSystem", new Transform<String, String>() {
                public String transform(String source0) {
                    String displayName = englishFile.getStringValue("//ldml/localeDisplayNames/types/type[@type=\""
                        + source0 +
                        "\"][@key=\"numbers\"]");
                    return displayName == null ? source0 : displayName + " (" + source0 + ")";
                }
            });
            // //ldml/localeDisplayNames/types/type[@type="%A"][@key="%A"]
            functionMap.put("datefield", new Transform<String, String>() {
                private final String[] datefield = {
                    "era", "year", "month", "week", "day", "weekday",
                    "hour", "dayperiod", "minute", "second", "zone"
                };

                public String transform(String source) {
                    String field = source.split("-")[0];
                    order = getIndex(field, datefield);
                    return source;
                }
            });
            // //ldml/dates/fields/field[@type="%A"]/relative[@type="%A"]
            functionMap.put("relativeDate", new Transform<String, String>() {
                private final String[] relativeDateField = {
                    "year", "month", "week", "day", "hour", "minute", "second",
                    "sun", "mon", "tue", "wed", "thu", "fri", "sat"
                };
                private final String[] longNames = {
                    "Year", "Month", "Week", "Day", "Hour", "Minute", "Second",
                    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
                };

                public String transform(String source) {
                    order = getIndex(source, relativeDateField) + 100;
                    return "Relative " + longNames[getIndex(source, relativeDateField)];
                }
            });
            // Sorts numberSystem items (except for decimal formats).
            functionMap.put("number", new Transform<String, String>() {
                private final String[] symbols = { "decimal", "group",
                    "plusSign", "minusSign", "percentSign", "perMille",
                    "exponential", "superscriptingExponent",
                    "infinity", "nan", "list", "currencies"
                };

                public String transform(String source) {
                    String[] parts = source.split("-");
                    order = getIndex(parts[0], symbols);
                    // e.g. "currencies-one"
                    if (parts.length > 1) {
                        suborder = new SubstringOrder(parts[1]);
                    }
                    return source;
                }
            });
            functionMap.put("numberFormat", new Transform<String, String>() {
                public String transform(String source) {
                    final List<String> fieldOrder = Arrays.asList(
                        "standard-decimal",
                        "standard-currency",
                        "standard-currency-accounting",
                        "standard-percent",
                        "standard-scientific");

                    if (fieldOrder.contains(source)) {
                        order = fieldOrder.indexOf(source);
                    } else {
                        order = fieldOrder.size();
                    }

                    return source;
                }
            });

            functionMap.put("localePattern", new Transform<String, String>() {
                public String transform(String source) {
                    // Put localeKeyTypePattern behind localePattern and
                    // localeSeparator.
                    if (source.equals("localeKeyTypePattern")) {
                        order = 10;
                    }
                    return source;
                }
            });
            functionMap.put("listOrder", new Transform<String, String>() {
                private String[] listParts = { "2", "start", "middle", "end" };

                @Override
                public String transform(String source) {
                    order = getIndex(source, listParts);
                    return source;
                }
            });
        }

        private static int getIndex(String item, String[] array) {
            for (int i = 0; i < array.length; i++) {
                if (item.equals(array[i])) {
                    return i;
                }
            }
            return -1;
        }

        static class HyphenSplitter {
            String main;
            String extras;

            String split(String source) {
                int hyphenPos = source.indexOf('-');
                if (hyphenPos < 0) {
                    main = source;
                    extras = "";
                } else {
                    main = source.substring(0, hyphenPos);
                    extras = source.substring(hyphenPos);
                }
                return main;
            }
        }

        /**
         * This converts "functions", like &month, and sets the order.
         * 
         * @param input
         * @param order
         * @return
         */
        private static String fix(String input, int orderIn) {
            input = RegexLookup.replace(input, args.value);
            order = orderIn;
            suborder = null;
            int pos = 0;
            while (true) {
                int functionStart = input.indexOf('&', pos);
                if (functionStart < 0) {
                    return input;
                }
                int functionEnd = input.indexOf('(', functionStart);
                int argEnd = input.indexOf(')', functionEnd);
                Transform<String, String> func = functionMap.get(input.substring(functionStart + 1,
                    functionEnd));
                String temp = func.transform(input.substring(functionEnd + 1, argEnd));
                input = input.substring(0, functionStart) + temp + input.substring(argEnd + 1);
                pos = functionStart + temp.length();
            }
        }

        /**
         * Collect all the paths for a CLDRFile, and make sure that they have
         * cached PathHeaders
         * 
         * @param file
         * @return immutable set of paths in the file
         */
        public Set<String> pathsForFile(CLDRFile file) {
            // make sure we cache all the path headers
            Set<String> filePaths = CollectionUtilities.addAll(file.fullIterable().iterator(), new HashSet<String>());
            for (String path : filePaths) {
                try {
                    fromPath(path); // call to make sure cached
                } catch (Throwable t) {
                    // ... some other exception
                }
            }
            return Collections.unmodifiableSet(filePaths);
        }

        /**
         * Returns those regexes that were never matched.
         * @return
         */
        public Set<String> getUnmatchedRegexes() {
            Map<String, RawData> outputUnmatched = new LinkedHashMap<String, RawData>();
            lookup.getUnmatchedPatterns(matchersFound, outputUnmatched);
            return outputUnmatched.keySet();
        }
    }

    /**
     * Return the territory used for the title of the Metazone page in the
     * Survey Tool.
     * 
     * @param source
     * @return
     */
    public static String getMetazonePageTerritory(String source) {
        String result = metazoneToPageTerritory.get(source);
        return result == null ? "ZZ" : result;
    }

    private static final List<String> COUNTS = Arrays.asList("zero", "one", "two", "few", "many", "other");

    private static int alphabeticCompare(String aa, String bb) {
        // workaround for ICU threading issue http://bugs.icu-project.org/trac/ticket/10215
        while (true) {
            try {
                return alphabetic.compare(aa, bb);
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }
    }

    public enum BaseUrl {
        //http://st.unicode.org/smoketest/survey?_=af&strid=55053dffac611328
        //http://st.unicode.org/cldr-apps/survey?_=en&strid=3cd31261bf6738e1
        SMOKE("http://st.unicode.org/smoketest/survey"),
        PRODUCTION("http://st.unicode.org/cldr-apps/survey");
        final String base;

        private BaseUrl(String url) {
            base = url;
        }
    }

    public String getUrl(BaseUrl baseUrl, String locale) {
        return getUrl(baseUrl.base, locale);
    }

    public String getUrl(String baseUrl, String locale) {
        return getUrl(baseUrl, locale, getOriginalPath());
    }

    /**
     * Map http://st.unicode.org/smoketest/survey  to http://st.unicode.org/smoketest etc
     * @param str
     * @return
     */
    public static String trimLast(String str) {
        int n = str.lastIndexOf('/');
        if (n == -1) return "";
        return str.substring(0, n + 1);
    }

    public static String getUrl(String baseUrl, String locale, String path) {
        return trimLast(baseUrl) + "v#/" + locale + "//" + StringId.getHexId(path);
    }

    // eg http://st.unicode.org/cldr-apps/survey?_=fr&x=Locale%20Name%20Patterns
    public static String getPageUrl(String baseUrl, String locale, PageId subsection) {
        return trimLast(baseUrl) + "v#/" + locale + "/" + subsection + "/";
    }

    public static String getLinkedView(String baseUrl, CLDRFile file, String path) {
        String value = file.getStringValue(path);
        if (value == null) {
            return null;
        }
        return SECTION_LINK + PathHeader.getUrl(baseUrl, file.getLocaleID(), path) + "'><em>view</em></a>";
    }
}
