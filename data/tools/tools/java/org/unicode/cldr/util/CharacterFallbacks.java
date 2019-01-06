package org.unicode.cldr.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class CharacterFallbacks {
    private static CharacterFallbacks SINGLETON = new CharacterFallbacks();
    private HashMap<Integer, List<String>> data = new HashMap<Integer, List<String>>();

    static public CharacterFallbacks make() {
        return SINGLETON;
    }

    public List<String> getSubstitutes(int cp) {
        return data.get(cp);
    }

    private CharacterFallbacks() {
        Factory cldrFactory = Factory.make(CldrUtility.DEFAULT_SUPPLEMENTAL_DIRECTORY, ".*");
        CLDRFile characterFallbacks = cldrFactory.make("characters", false);
        XPathParts parts = new XPathParts();

        for (Iterator<String> it = characterFallbacks.iterator("//supplementalData/characters/",
            CLDRFile.ldmlComparator); it.hasNext();) {
            String path = it.next();
            parts.set(path);
            /*
             * <character value = "―">
             * <substitute>—</substitute>
             * <substitute>-</substitute>
             */
            String value = parts.getAttributeValue(-2, "value");
            if (value.codePointCount(0, value.length()) != 1) {
                throw new IllegalArgumentException("Illegal value in " + path);
            }
            int cp = value.codePointAt(0);
            String substitute = characterFallbacks.getStringValue(path);

            List<String> substitutes = data.get(cp);
            if (substitutes == null) {
                data.put(cp, substitutes = new ArrayList<String>());
            }
            substitutes.add(substitute);
        }
        CldrUtility.protectCollection(data);
    }
}
