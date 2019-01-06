//
//  SupplementalData.java
//  fivejay
//
//  Created by Steven R. Loomis on 16/01/2007.
//  Copyright 2007-2011 IBM. All rights reserved.
//
//
// TODO: replace string literals with constants

package org.unicode.cldr.util;

import java.util.Hashtable;
import java.util.Set;
import java.util.regex.Pattern;

import org.unicode.cldr.icu.LDMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class that abstracts out some of the supplementalData
 * and parses it into more readily usable form. It is certinly not complete,
 * and the interface should not be considered stable.
 * 
 * Generally, it tries to only parse data as it is used.
 * 
 * @deprecated use SupplementalDataInfo instead.
 */
public class SupplementalData {

    String pathName = null;
    private Document supplementalDocument = null;
    private Document supplementalMetaDocument = null;
    private SupplementalDataInfo sdi = null;

    static final String SPLIT_PATTERN = "[, \t\u00a0\\s]+"; // whitespace

    public static String[] split(String list) {
        if ((list == null) || ((list = list.trim()).length() == 0)) {
            return new String[0];
        }
        return list.trim().split(SPLIT_PATTERN);
    }

    /**
     * Construct a new SupplementalData from the specified fileName, i.e. "...../supplemental"
     */
    public SupplementalData(String pathName) {
        this.pathName = pathName;
        supplementalDocument = LDMLUtilities.parse(pathName + "/supplementalData.xml", false);
        if (supplementalDocument == null) {
            throw new InternalError("Can't parse supplemental: " + pathName + "/supplementalData.xml");
        }
        supplementalMetaDocument = LDMLUtilities.parse(pathName + "/supplementalMetadata.xml", false);
        if (supplementalMetaDocument == null) {
            throw new InternalError("Can't parse metadata: " + pathName + "/supplementalMetadata.xml");
        }
        sdi = SupplementalDataInfo.getInstance(pathName);
    }

    /**
     * some supplemental data parsing stuff
     */
    public Document getSupplemental() {
        return supplementalDocument;
    }

    public Document getMetadata() {
        return supplementalMetaDocument;
    }

    /**
     * Territory alias parsing
     */
    Hashtable territoryAlias = null;

    public synchronized Hashtable getTerritoryAliases() {
        if (territoryAlias == null) {
            Hashtable ta = new Hashtable();
            NodeList territoryAliases =
                LDMLUtilities.getNodeList(getMetadata(),
                    "//supplementalData/metadata/alias/territoryAlias");
            if (territoryAliases.getLength() == 0) {
                System.err.println("no territoryAliases found");
            }
            for (int i = 0; i < territoryAliases.getLength(); i++) {
                Node item = territoryAliases.item(i);

                String type = LDMLUtilities.getAttributeValue(item, LDMLConstants.TYPE);
                String replacement = LDMLUtilities.getAttributeValue(item, LDMLConstants.REPLACEMENT);
                String[] replacementList = split(replacement);

                ta.put(type, replacementList);
                // System.err.println(type + " -> " + replacement);
            }
            territoryAlias = ta;
        }
        return territoryAlias;
    }

    public Set getObsoleteTerritories() {
        if (territoryAlias == null) {
            getTerritoryAliases();
        }
        return territoryAlias.keySet();
    }

    /**
     * some containment parsing stuff
     */

    private Hashtable tcDown = null; // String -> String[]
    private Hashtable tcUp = null; // String -> String (parent)

    public String[] getContainedTerritories(String territory) {
        if (tcUp == null) {
            parseTerritoryContainment();
        }
        return (String[]) tcDown.get(territory);
    }

    public String getContainingTerritory(String territory) {
        if (tcUp == null) {
            parseTerritoryContainment();
        }
        return (String) tcUp.get(territory);
    }

    void findParents(Hashtable u, Hashtable d, String w, int n)
    {
        if (n == 0) {
            throw new InternalError("SupplementalData:findParents() recursed too deep, at " + w);
        }
        String[] children = (String[]) d.get(w);
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            u.put(children[i], w);
            findParents(u, d, children[i], n - 1);
        }
    }

    private synchronized void parseTerritoryContainment()
    {
        Set ot = getObsoleteTerritories();
        if (tcDown == null) {
            Hashtable d = new Hashtable();
            Hashtable u = new Hashtable();

            NodeList territoryContainment =
                LDMLUtilities.getNodeList(getSupplemental(),
                    "//supplementalData/territoryContainment/group");
            for (int i = 0; i < territoryContainment.getLength(); i++) {
                Node item = territoryContainment.item(i);

                String type = LDMLUtilities.getAttributeValue(item, LDMLConstants.TYPE);
                String contains = LDMLUtilities.getAttributeValue(item, LDMLConstants.CONTAINS);
                String[] containsList = split(contains);

                // now, add them
                d.put(type, containsList);
            }

            // link the children to the parents
            findParents(u, d, "001", 15);

            tcUp = u;
            tcDown = d;
        }
    }

    String multiZone[] = null;
    Set multiZoneSet = null;

    public String resolveParsedMetazone(String metazone, String territory)
    {
        Node mzLookup = null;
        String result = "Etc/GMT";

        mzLookup = LDMLUtilities.getNode(getSupplemental(),
            "//supplementalData/timezoneData/mapTimezones[@type=\"metazones\"]/mapZone[@other=\"" + metazone
                + "\"][@territory=\"" + territory + "\"]");

        if (mzLookup == null) {
            mzLookup = LDMLUtilities.getNode(getSupplemental(),
                "//supplementalData/timezoneData/mapTimezones[@type=\"metazones\"]/mapZone[@other=\"" + metazone
                    + "\"][@territory=\"001\"]");

            if (mzLookup == null) {
                return result;
            }
        }

        result = LDMLUtilities.getAttributeValue(mzLookup, LDMLConstants.TYPE);
        return result;
    }

    Hashtable validityVariables = new Hashtable();

    // variables
    synchronized String getValidityVariable(String id, String type) {
        String key = id + "/" + type;
        String ret = (String) validityVariables.get(key);
        if (ret != null) {
            return ret;
        }

        try {
            NodeList defaultContent =
                LDMLUtilities.getNodeList(getMetadata(),
                    "//supplementalData/metadata/validity/variable");

            int len = defaultContent.getLength();
            for (int i = 0; i < len; i++) {
                Node defaultContentItem = defaultContent.item(i);

                String nodeId = LDMLUtilities.getAttributeValue(defaultContentItem, "id");
                if (!id.equals(nodeId)) {
                    continue;
                }
                String nodeType = LDMLUtilities.getAttributeValue(defaultContentItem, "type");
                if (!type.equals(nodeType)) {
                    continue;
                }

                ret = LDMLUtilities.getNodeValue(defaultContentItem);

                validityVariables.put(key, ret);
                return ret;
            }
        } catch (Throwable t) {
            System.err.println("Looking for validity variable " + id + "/" + type + " -> " + t.toString());
            t.printStackTrace();
            throw new InternalError("Looking for validity variable " + id + "/" + type + " -> " + t.toString());
        }
        return null;
    }

    public String[] getValidityChoice(String id) {
        return split(getValidityVariable(id, "choice"));
    }

    public Pattern getValidityRegex(String id) {
        String regex = getValidityVariable(id, "regex");
        if (regex == null) {
            return null;
        } else {
            return Pattern.compile(regex);
        }
    }
}
