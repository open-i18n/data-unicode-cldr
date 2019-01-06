package org.unicode.cldr.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.SupplementalDataInfo;

import com.ibm.icu.dev.util.BagFormatter;

public class DumpCoverage {

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {

        long start_time = System.currentTimeMillis();
        SupplementalDataInfo sdi = SupplementalDataInfo.getInstance();
        Factory cldrFactory = Factory.make(CLDRPaths.MAIN_DIRECTORY, ".*");
        Set<String> languages = cldrFactory.getAvailableLanguages();
        PrintWriter out = BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, "coverageDump.txt");
        for (String lang : languages) {
            CLDRFile cf = cldrFactory.makeWithFallback(lang);
            Set<String> paths = new TreeSet<String>();
            cf.getPaths("//ldml", null, paths);
            System.out.println("Dumping coverage for locale --> " + lang);
            for (String path : paths) {
                int cov = sdi.getCoverageValue(path, lang);
                out.println(lang + " [" + cov + "] --> " + path);

            }
        }
        out.close();
        long end_time = System.currentTimeMillis();
        System.out.println(end_time - start_time + " ms elapsed...");
    }
}
