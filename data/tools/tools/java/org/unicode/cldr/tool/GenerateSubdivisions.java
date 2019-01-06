package org.unicode.cldr.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.ChainedMap;
import org.unicode.cldr.util.ChainedMap.M3;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.DtdType;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.PatternCache;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.StandardCodes.LstrField;
import org.unicode.cldr.util.StandardCodes.LstrType;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XPathParts;

import com.google.common.base.Splitter;
import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.dev.util.TransliteratorUtilities;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.util.ULocale;

public class GenerateSubdivisions {

    static final RuleBasedCollator ROOT_COL = (RuleBasedCollator) Collator.getInstance(ULocale.ENGLISH);
    static {
        ROOT_COL.setNumericCollation(true);
        ROOT_COL.freeze();
    }

    static Map<String,String> NAME_CORRECTIONS = new HashMap<>();
    static {
        Splitter semi = Splitter.on(';').trimResults();
        for (String s : FileUtilities.in(CLDRPaths.DATA_DIRECTORY + "iso/","en-subdivisions-corrections.txt")) {
            if (s.startsWith("#")) {
                continue;
            }
            s = s.trim();
            if (s.isEmpty()) {
                continue;
            }
            List<String> parts = semi.splitToList(s);
            NAME_CORRECTIONS.put(parts.get(0), parts.get(1));
        }
    }

    static final Normalizer2 nfc = Normalizer2.getNFCInstance();

    public static void main(String[] args) throws IOException {
        loadIso();
        loadWiki();
        try (PrintWriter pw = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "subdivision/subdivisions.xml")) {
            SubdivisionNode.printXml(pw);
        }
        try (PrintWriter pw = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "subdivision/subdivisionAliases.txt")) {
            SubdivisionNode.printAliases(pw);
        }
        try (PrintWriter pw = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "subdivision/en.xml")) {
            SubdivisionNode.printEnglish(pw);
        }
        try (PrintWriter pw = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "subdivision/categories.txt")) {
            SubdivisionNode.printSamples(pw);
        }
        try (PrintWriter pw = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "subdivision/en.txt")) {
            SubdivisionNode.printEnglishComp(pw);
        }
        try (PrintWriter pw = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "subdivision/en-full.txt")) {
            SubdivisionNode.printEnglishCompFull(pw);
        }
        try (PrintWriter pw = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "subdivision/missing-mid.txt")) {
            SubdivisionNode.printMissingMIDs(pw);
        }
    }

    static final Map<String,SubdivisionNode> ID_TO_NODE = new HashMap<>();

    static class SubdivisionNode {
        private static final SupplementalDataInfo SDI = SupplementalDataInfo.getInstance();
        
        static LocaleDisplayNames ENGLISH = LocaleDisplayNames.getInstance(ULocale.ENGLISH);
        static final M3<String, String, String> NAMES = ChainedMap.of(
            new TreeMap<String,Object>(),
            new TreeMap<String,Object>(),
            String.class
            );
        static final Map<String,String> TO_COUNTRY_CODE = new TreeMap<String,String>();
        static final Relation<String,String> ID_SAMPLE = Relation.of(new TreeMap<String,Set<String>>(), TreeSet.class);
        static final Map<String,String> SUB_TO_CAT = new TreeMap<>();
        static final SubdivisionNode BASE = new SubdivisionNode("001", null).addName("en", "World");
        static final Relation<String,String> REGION_CONTAINS = Relation.of(new TreeMap<String,Set<String>>(), TreeSet.class);

        static public void addName(String code, String lang, String value) {
            int parenPos = value.indexOf("(see also separate country");
            if (parenPos >= 0) {
                /*
    Error: (TestSubdivisions.java:66) : country BQ = subdivisionNL-BQ1: expected "Caribbean Netherlands", got "Bonaire"
    Error: (TestSubdivisions.java:66) : country BQ = subdivisionNL-BQ2: expected "Caribbean Netherlands", got "Saba"
    Error: (TestSubdivisions.java:66) : country BQ = subdivisionNL-BQ3: expected "Caribbean Netherlands", got "Sint Eustatius"
    Error: (TestSubdivisions.java:66) : country SJ = subdivisionNO-21: expected "Svalbard & Jan Mayen", got "Svalbard"
    Error: (TestSubdivisions.java:66) : country SJ = subdivisionNO-22: expected "Svalbard & Jan Mayen", got "Jan Mayen"
                 */
                String paren = value.substring(value.length()-3,value.length()-1);
                if (!paren.equals("BQ") && !paren.equals("SJ")) {
                    String old = TO_COUNTRY_CODE.get(code);
                    if (old != null) {
                        System.err.println("Duplicate: " + code + "\t" + old + "\t" + paren);
                    }
                    TO_COUNTRY_CODE.put(code, paren);   
                }
                value = value.substring(0,parenPos).trim();
            }
            value = value.replace("*", "");
            NAMES.put(code, lang, value);
        }

        public static void printMissingMIDs(PrintWriter pw) {
            for (Entry<String, String> entry: WIKIDATA_TO_MID.entrySet()) {
                String mid = entry.getValue();
                if (!mid.isEmpty()) {
                    continue;
                }
                String subCode = entry.getKey();
                String wiki = clean(getWikiName(subCode));
                String iso = clean(getIsoName(subCode));
                String countryCode = subCode.substring(0,2);
                String cat = SUB_TO_CAT.get(subCode);
                String catName = getIsoName(cat);
                pw.append(
                    ENGLISH.regionDisplayName(countryCode)
                    + "\t" + mid
                    + "\t" + subCode
                    + "\t" + catName
                    + "\t" + wiki
                    + "\t" + iso
                    + "\n"
                    );
            }
        }


        public static void printEnglishComp(Appendable output) throws IOException {
            Set<String> countEqual = new TreeSet<>();
            String lastCC = null;
            output.append("Country\tMID\tSubdivision\tCLDR\tISO\tWikidata\tEqual\n");
            for (Entry<String, Set<String>> entry : SubdivisionNode.REGION_CONTAINS.keyValuesSet()) {
                final String countryCode = entry.getKey();
                if (!countryCode.equals(lastCC)) {
                    if (lastCC != null && countEqual.size() != 0) {
                        output.append(ENGLISH.regionDisplayName(lastCC) + "\t\t\tEquals:\t" + countEqual.size() + "\t" + countEqual + "\n");
                    }
                    countEqual.clear();;
                    lastCC = countryCode;
                }
                for (String value : entry.getValue()) {
                    String cldrName = getBestName(value);
                    String wiki = getWikiName(value);
                    final String iso = getIsoName(value);
                    if (iso.equals(wiki)) {
                        countEqual.add(iso);
                        continue;
                    }
                    output.append(
                        ENGLISH.regionDisplayName(countryCode)
                        + "\t" + WIKIDATA_TO_MID.get(value)
                        + "\t" + cldrName
                        + "\t" + value
                        + "\t" + iso
                        + "\t" + wiki
                        + "\n"
                        );
                }    
            }
            if (countEqual.size() != 0) {
                output.append(ENGLISH.regionDisplayName(lastCC) + "\t\t\tEquals:\t" + countEqual.size() + "\t" + countEqual + "\n");
            }
        }

        public static void printEnglishCompFull(Appendable output) throws IOException {
            output.append("Country\tMID\tSubdivision\tCLDR\tISO\tWikidata\n");
            for (Entry<String, Set<String>> entry : SubdivisionNode.REGION_CONTAINS.keyValuesSet()) {
                final String countryCode = entry.getKey();
                for (String value : entry.getValue()) {
                    String cldrName = getBestName(value);
                    //getBestName(value);
                    String wiki = getWikiName(value);
                    final String iso = getIsoName(value);
                    output.append(
                        ENGLISH.regionDisplayName(countryCode)
                        + "\t" + WIKIDATA_TO_MID.get(value)
                        + "\t" + value
                        + "\t" + cldrName
                        + "\t" + iso
                        + "\t" + wiki
                        + "\n"
                        );
                }    
            }
        }

        private static String getWikiName(String value) {
            String name = WIKIDATA_LANG_NAME.get(value,"en");
            if (name != null) {
                return name;
            }
            name = WIKIDATA_LANG_NAME.get(value,"es");
            if (name != null) {
                return name;
            }
            name = WIKIDATA_LANG_NAME.get(value,"fr");
            if (name != null) {
                return name;
            }
            Map<String, String> data = WIKIDATA_LANG_NAME.get(value);
            // try Spanish, then French, then first other
            if (data != null) {
                return data.entrySet().iterator().next().getValue(); // get first 
            }
            return null;
        }

        static final String[] CRUFT = {
            "Emirate", 
            "Parish", 
            "County", 
            "District", 
            "Region", 
            "Province of",
            "Province", 
            "Republic", 
            ", Barbados", 
            ", Burkina Faso", 
            "Governorate", 
            "Department", 
            "Canton of", 
            "(Région des)", 
            "(Région du)", 
            "(Région de la)", 
            "Autonomous", 
            "Archipelago of",
            "Canton", 
            "kanton",
            ", Bahamas",
            "province",
            "(Région)",
            "(Région de l')",
            ", Cameroon",
            "State of",
            "State",
            "Metropolitan Borough of",
            "London Borough of",
            "Royal Borough of",
            "Borough of",
            "Borough",
            "Council of",
            "Council",
            "City of",
            ", The",
            "prefecture",
            "Prefecture",
            "municipality"
        };

        static final Pattern CRUFT_PATTERN = PatternCache.get("(?i)\\b" + CollectionUtilities.join(CRUFT, "|") + "\\b");
        static final Pattern BRACKETED = PatternCache.get("\\[.*\\]");
        static String clean(String input) {
            if (input == null) {
                return input;
            }
            // Quick & dirty
            input = BRACKETED.matcher(input).replaceAll("");
            input = CRUFT_PATTERN.matcher(input).replaceAll("");
//            for (String cruft : CRUFT) {
//                int pos = input.indexOf(cruft);
//                if (pos >= 0) {
//                    input = input.substring(0,pos) + input.substring(pos + cruft.length());
//                }
//            }
            input.replace("  ", " ");
            if (input.endsWith(",")) {
                input = input.substring(0,input.length()-1);
            }
            return fixName(input);
        }

        public static void printEnglish(Appendable output) throws IOException {
            // <subdivisions>
            // <subdivisiontype="NZ-AUK">Auckland</territory>
            output.append(
                header(DtdType.ldml)
                + "<ldml>\n"
                + "\t<identity>\n"
                + "\t\t<version number=\"$Revision: 11611 $\"/>\n"
                + "\t\t<language type=\"en\"/>\n"
                + "\t</identity>\n"
                + "\t<localeDisplayNames>\n"
                + "\t\t<subdivisions>\n");
            Set<String> missing = new LinkedHashSet<>();
            Set<String> skipped = new LinkedHashSet<>();

            for (String regionCode : codeToData.keySet()) {
                if (!isKosher(regionCode)) {
                    if (regionCode.length() != 3) {
                        skipped.add(regionCode);
                    }
                    continue;
                }
                SubdivisionNode regionNode = ID_TO_NODE.get(regionCode);
                output.append("\t\t<!-- ")
                .append(regionCode).append(" : ")
                .append(TransliteratorUtilities.toXML.transform(ENGLISH.regionDisplayName(regionCode)));
                if (regionNode == null) {
                    output.append(" : NO SUBDIVISIONS -->\n");
                    continue;
                }
                output.append(" -->\n");
                
                Set<SubdivisionNode> ordered = new LinkedHashSet<>();
                addChildren(ordered, regionNode.children);

                for (SubdivisionNode node : ordered) {
                    String name = getBestName(node.code);
                    if (name == null) {
                        missing.add(node.code + ": " + getIsoName(node.code));
                        continue;
                    }
                    String upper = UCharacter.toUpperCase(name);
                    @SuppressWarnings("deprecation")
                    String title = UCharacter.toTitleFirst(ULocale.ROOT, name);
                    if (name.equals(upper) || !name.equals(title)) {
                        System.out.println("Suspicious name: " + name);
                    }

                    SubdivisionNode sd = ID_TO_NODE.get(node.code);

                    String level = sd.level == 1 ? "" : "\t<!-- in " + sd.parent.code 
                        + " : " + TransliteratorUtilities.toXML.transform(getBestName(sd.parent.code)) + " -->";
                    output.append("\t\t\t<subdivision type=\"").append(node.code).append("\">")
                    .append(TransliteratorUtilities.toXML.transform(name))
                    .append("</subdivision>")
                    .append(level)
                    .append('\n');
                }
            }
            output.append(
                "\t\t</subdivisions>\n"
                    + "\t</localeDisplayNames>\n"
                    + "</ldml>");
            System.out.println("Skipping: " + skipped);
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("No name for: " + missing.size() + ", " + missing);
            }
        }

        static Map<String, R2<List<String>, String>> territoryAliases = SDI.getLocaleAliasInfo().get("territory");
        static Set<String> containment = SDI.getContainers();
        static Map<String, Map<LstrField, String>> codeToData = StandardCodes.getEnumLstreg().get(LstrType.region);

        private static boolean isKosher(String regionCode) {
            if (regionCode.equals("001")) {
                return false;
            }
            if (territoryAliases.containsKey(regionCode) 
                || containment.contains(regionCode)
                || codeToData.get(regionCode).get(LstrField.Description).contains("Private use")) {
                Set<String> rc = REGION_CONTAINS.get(regionCode);
                if (rc != null) {
                    throw new IllegalArgumentException("? " + regionCode + ": " + rc);
                }
                return false;
            }
            return true;
        }

        private static void addChildren(Set<SubdivisionNode> ordered, Map<String, SubdivisionNode> children2) {
            TreeMap<String, SubdivisionNode> temp = new TreeMap<>(ROOT_COL);
            temp.putAll(children2);
            ordered.addAll(temp.values());
            for (SubdivisionNode n : temp.values()) {
                if (!n.children.isEmpty()) {
                    addChildren(ordered, n.children);
                }
            }
        }

        static  final Map<String, R2<List<String>, String>> subdivisionAliases = SDI.getLocaleAliasInfo().get("subdivision");

        private static String getBestName(String value) {
            String cldrName = NAME_CORRECTIONS.get(value);
            if (cldrName != null) {
                return fixName(cldrName); 
            }
            R2<List<String>, String> subdivisionAlias = subdivisionAliases.get(value);
            if (subdivisionAlias != null) {
                String country = subdivisionAlias.get0().get(0);
                cldrName = CLDRConfig.getInstance().getEnglish().getName(CLDRFile.TERRITORY_NAME, country);
                return fixName(cldrName);
            }
            cldrName = CldrUtility.ifNull(NAME_CORRECTIONS.get(value), ChartSubdivisions.getSubdivisionName(value));
            return fixName(cldrName);
        }

        private static String fixName(String name) {
            return nfc.normalize(name.replace('\'', '’').replace("  ", " ").trim());
        }

        final String code;
        final int level;
        final SubdivisionNode parent;
        final Map<String, SubdivisionNode> children = new LinkedHashMap<>();

        public SubdivisionNode(String code, SubdivisionNode parent) {
            this.code = code;
            this.level = parent == null ? -1 : parent.level+1;
            this.parent = parent;
            ID_TO_NODE.put(code,this);
        }
        public SubdivisionNode addName(String lang, String value) {
            NAMES.put(code, lang, value);
            return this;
        }
        static final SubdivisionNode addNode(SubdivisionNode lastSubdivision, String subdivision) {
            // "NZ-S", x
            String region = subdivision.substring(0,subdivision.indexOf('-'));
            REGION_CONTAINS.put(region, subdivision);
            if (lastSubdivision == null) {
                lastSubdivision = BASE.children.get(region);
                if (lastSubdivision == null) {
                    lastSubdivision = new SubdivisionNode(region, BASE).addName("en", ENGLISH.regionDisplayName(region));
                    BASE.children.put(region, lastSubdivision);
                }
                return add(lastSubdivision, subdivision);
            }
            add(lastSubdivision, subdivision);
            return lastSubdivision;
        }

        private static SubdivisionNode add(SubdivisionNode subdivisionNode1, String subdivision2) {
            SubdivisionNode subdivisionNode2 = subdivisionNode1.children.get(subdivision2);
            if (subdivisionNode2 == null) {
                subdivisionNode2 = new SubdivisionNode(subdivision2, subdivisionNode1);
            }
            subdivisionNode1.children.put(subdivision2, subdivisionNode2);
            return subdivisionNode2;
        }

        private static String getName(SubdivisionNode base2) {
            return getIsoName(base2.code);
        }
        private static String getIsoName(String code) {
            if (code == null) {
                return null;
            }
            Map<String, String> map = NAMES.get(code);
            if (map == null) {
                return "???";
            }
            String name = map.get("en");
            if (name != null) {
                return name;
            }
            name = map.get("es");
            if (name != null) {
                return name;
            }
            name = map.get("fr");
            if (name != null) {
                return name;
            }
            if (name == null) {
                name = map.entrySet().iterator().next().getValue();
            }
            return name;
        }

        public static void print() {
            print(BASE, 0);
            for (Entry<String, String> entry : TO_COUNTRY_CODE.entrySet()) {
                System.out.println(entry.getKey() + "\t" + entry.getValue());
            }
        }
        private static void print(SubdivisionNode base2, int indent) {
            final String indentString = Utility.repeat("\t", indent);
            System.out.println(indentString + base2.code 
                + "\t" + getName(base2));     
            if (base2.children.isEmpty()) {
                return;
            }
            for (SubdivisionNode child : base2.children.values()) {
                print(child,indent+1);
            }
        }

        static void printXml(Appendable output) throws IOException {

            /*
<subdivisionContainment>
    <group type="NZ" category="island" contains="NZ-N NZ-S"/> <!-- New Zealand -->
    <group type="NZ" category="special island authority" contains="NZ-CIT"/> <!-- New Zealand -->
    <group type="NZ-N" contains="NZ-AUK NZ-BOP NZ-GIS NZ-HKB NZ-MWT NZ-NTL NZ-AUK NZ-TKI NZ-WGN NZ-WKO"/> <!-- North Island -->
    <group type="NZ-S" contains="NZ-CAN NZ-MBH NZ-STL NZ-NSN NZ-OTA NZ-TAS NZ-WTC"/> <!-- South Island -->
  </subdivisionContainment>
             */
            output.append(
                header(DtdType.supplementalData)
                +"\n"
                +"<supplementalData>\n"
                +"    <version number='$Revision: 8268 $'/>\n"
                +"\t<subdivisionContainment>\n");
            printXml(output, BASE, 0);
            output.append("\t</subdivisionContainment>\n</supplementalData>\n");
        }
        
        private static String header(DtdType type) {
            return "<?xml version='1.0' encoding='UTF-8' ?>\n"
                +"<!DOCTYPE " + type // supplementalData
                + " SYSTEM '../../" + type.dtdPath + "'>\n" // "common/dtd/ldmlSupplemental.dtd"
                +"<!--\n"
                +"Copyright © 1991-2013 Unicode, Inc.\n"
                +"CLDR data files are interpreted according to the LDML specification (http://unicode.org/reports/tr35/)\n"
                +"For terms of use, see http://www.unicode.org/copyright.html\n"
                +"-->\n";
        }

        private static void printAliases(Appendable output) throws IOException {
            for (Entry<String, String> entry : TO_COUNTRY_CODE.entrySet()) {
                // <languageAlias type="art_lojban" replacement="jbo" reason="deprecated"/> <!-- Lojban -->
                output.append("<subdivisionAlias"
                    + " type=\"" + entry.getKey() + "\""
                    + " replacement=\"" + entry.getValue() + "\""
                    + " reason=\"" + "overlong" + "\"/>\n");
            }
        }

        private static void printXml(Appendable output, SubdivisionNode base2, int indent) throws IOException {
            if (base2.children.isEmpty()) {
                return;
            }
            String type = base2.code;
            if (base2 != BASE) {
                int hyphenPos = type.indexOf('-');
                if (hyphenPos >= 0) {
                    String subtype = type.substring(hyphenPos+1);
                    type = type.substring(0,hyphenPos);
                    output.append("\t\t" + "<subgroup"
                        + " type=\"" + type + "\""
                        + " subtype=\"" + subtype + "\""
                        + " contains=\"");
                } else {
                    output.append("\t\t" + "<subgroup"
                        + " type=\"" + type + "\""
                        + " contains=\"");
                }
                boolean first = true;
                for (String child : base2.children.keySet()) {
                    if (first) {
                        first = false;
                    } else {
                        output.append(' ');
                    }
                    String subregion = child.substring(child.indexOf('-')+1);
                    output.append(subregion);
                }
                output.append("\"/>\n");
            }
            for (SubdivisionNode child : base2.children.values()) {
                printXml(output, child, indent);
            }
        }
        public static void addIdSample(String id, String value) {
            SUB_TO_CAT.put(value, id);
            ID_SAMPLE.put(getIsoName(id), value);            
        }

        public static void printSamples(Appendable pw) throws IOException {
            Set<String> seen = new HashSet<>();
            for (Entry<String, Set<String>> entry : ID_SAMPLE.keyValuesSet()) {
                pw.append(entry.getKey());
                //int max = 10;
                seen.clear();
                for (String sample : entry.getValue()) {
                    String region = sample.substring(0,2);
                    if (seen.contains(region)) {
                        continue;
                    }
                    seen.add(region);
                    pw.append(";\t" + ENGLISH.regionDisplayName(region) + ": " + getIsoName(sample)
                        + " (" + sample + ")");
                    //if (--max < 0) break;
                }
                pw.append(System.lineSeparator());
            }
        }
    }

    static ChainedMap.M3<String,String,String> WIKIDATA_LANG_NAME 
    = ChainedMap.of(new TreeMap<String,Object>(), new TreeMap<String,Object>(), String.class);
    static Map<String,String> WIKIDATA_TO_MID = new TreeMap<>();

    private static void loadWiki() {
        Splitter TAB =  Splitter.on('\t').trimResults();
        for (String line : FileUtilities.in(CLDRPaths.DATA_DIRECTORY + "iso/","subdivision-names-wikidata.txt")) {
            // AD-02    Q24260  /m/... an  Canillo
            List<String> data = TAB.splitToList(line);
            WIKIDATA_LANG_NAME.put(data.get(0), data.get(3), data.get(4));
            WIKIDATA_TO_MID.put(data.get(0), data.get(2));
        }
    }

    public static void loadIso() {
        //    <country id="AD" version="16">
        //           <subdivision-code footnote="*">AD-02</subdivision-code>
        //             <subdivision-locale lang3code="eng" xml:lang="en">
        //                  <subdivision-locale-name>Otago</subdivision-locale-name>

        List<Pair<String, String>> pathValues = XMLFileReader.loadPathValues(
            CLDRPaths.DATA_DIRECTORY + "iso/iso_country_codes.xml", 
            new ArrayList<Pair<String, String>>(), false);
        XPathParts parts = new XPathParts();
        int maxIndent = 0;
        SubdivisionNode lastNode = null;
        String lastCode = null;

        for (Pair<String, String> pair : pathValues) {
            String path = pair.getFirst();
            boolean code = path.contains("/subdivision-code");
            boolean name = path.contains("/subdivision-locale-name");
            boolean nameCat = path.contains("/category-name");

            //    <country id="AD" version="16">
            //       <category id="262">
            //  <category-name lang3code="fra" xml:lang="fr">paroisse</category-name>
            //  <category-name lang3code="eng" xml:lang="en">parish</category-name>
            // also languages in region...
            if (!code && !name && !nameCat) {
                continue;
            }
            parts.set(path);
            String value = pair.getSecond();
            if (name) {
                int elementNum = -2;
                String lang = parts.getAttributeValue(elementNum, "xml:lang");
                if (lang == null) {
                    lang = parts.getAttributeValue(elementNum, "lang3code");
                }
                SubdivisionNode.addName(lastCode, lang, value);
                //output.println(count + Utility.repeat("\t", indent) + "\tlang=" + lang + ":\t«" + value + "»\t");     
            } else if (nameCat) {
                //country-codes[@generated="2015-05-04T15:40:13.424465+02:00"]/country[@id="AD"][@version="16"]/category[@id="262"]/category-name[@lang3code="fra"][@xml:lang="fr"]
                int elementNum = -1;
                String lang = parts.getAttributeValue(elementNum, "xml:lang");
                if (lang == null) {
                    lang = parts.getAttributeValue(elementNum, "lang3code");
                }
                String category = parts.getAttributeValue(-2, "id");
                SubdivisionNode.addName(category, lang, value);
                //output.println(count + Utility.repeat("\t", indent) + "\tlang=" + lang + ":\t«" + value + "»\t");     
            } else {
                int countSubdivision = 0;
                for (int i = 0; i < parts.size(); ++i) {
                    if (parts.getElement(i).equals("subdivision")) {
                        ++countSubdivision;
                    }
                }
                if (maxIndent < countSubdivision) {
                    maxIndent = countSubdivision;
                }
                if (countSubdivision == 1) {
                    lastNode = SubdivisionNode.addNode(null, value);
                } else {
                    lastNode = SubdivisionNode.addNode(lastNode, value);
                }
                lastCode = value;
                int subdivisionElement = parts.findElement("subdivision");
                String id = parts.getAttributeValue(subdivisionElement, "category-id");
                SubdivisionNode.addIdSample(id, value);
                //<subdivision category-id="262">//<subdivision-code footnote="*">AD-06</subdivision-code>
                // <subdivision category-id="262">
                //output.println(++count + Utility.repeat("\t", indent) + "code=" + value);
            }
        }
    }
}
