package org.unicode.cldr.util;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.util.ULocale;

public final class Timer {
    private long startTime;
    private long duration;
    {
        start();
    }

    public void start() {
        startTime = System.nanoTime();
        duration = Long.MIN_VALUE;
    }

    public long getDuration() {
        if (duration == Long.MIN_VALUE) {
            duration = System.nanoTime() - startTime;
        }
        return duration;
    }

    public long stop() {
        return getDuration();
    }

    public String toString() {
        return nf.format(getDuration()) + "ns";
    }

    public String toString(Timer other) {
        return toString(1L, other.getDuration());
    }

    public String toString(long iterations) {
        return nf.format(getDuration() / iterations) + "ns";
    }

    public String toString(long iterations, long other) {
        return nf.format(getDuration() / iterations) + "ns" + "\t(" + pf.format((double) getDuration() / other - 1D)
            + ")";
    }

    private static DecimalFormat nf = (DecimalFormat) NumberFormat.getNumberInstance(ULocale.ENGLISH);
    private static DecimalFormat pf = (DecimalFormat) NumberFormat.getPercentInstance(ULocale.ENGLISH);
    static {
        pf.setMaximumFractionDigits(1);
        pf.setPositivePrefix("+");
    }
}