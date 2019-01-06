package org.unicode.cldr.tool;

import java.io.IOException;
import java.io.PrintWriter;

import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.DtdData;
import org.unicode.cldr.util.DtdType;

import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.ULocale;

public class GenerateDtd {
    public static void main(String[] args) throws IOException {
        //System.setProperty("show_all", "true");
        for (DtdType type : DtdType.values()) {
            if (type == DtdType.ldmlICU) {
                continue;
            }
            DtdData data = DtdData.getInstance(type);
            String name = type.toString();
            if (!name.startsWith("ldml")) {
                name = "ldml" + UCharacter.toTitleFirst(ULocale.ENGLISH, name);
                if (name.endsWith("Data")) {
                    name = name.substring(0, name.length() - 4);
                }
            }
            try (PrintWriter out = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY + "dtd/", name + ".dtd")) {
                out.println(data);
            }
        }
    }
}
