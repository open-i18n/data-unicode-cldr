package org.unicode.cldr.tool;

public class GenerateAllCharts {
    public static void main(String[] args) throws Exception {
        ShowLanguages.main(args);
        GenerateBcp47Text.main(args);
        GenerateSidewaysView.main(args);
        ShowData.main(args);
        GenerateTransformCharts.main(args);
        ShowKeyboards.main(args);
    }
}
