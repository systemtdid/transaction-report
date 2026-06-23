import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;

import io.github.de8tech.thaipdf.shaping.openhtml.ShapedPdfBoxRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Renders thai_test.html to a searchable Thai PDF using the
 * thai-pdf-glyphshaping library.
 *
 * <p>Place font files in the {@code fonts/} subdirectory before running:
 * <ul>
 *   <li>{@code NotoSansThai-Regular.ttf} — required; free from Google Fonts</li>
 *   <li>{@code tahoma.ttf}               — optional; bundled with Windows / Office</li>
 *   <li>{@code THSarabunNew.ttf}         — optional; exercises the legacy PUA path</li>
 * </ul>
 *
 * <p>Run with: {@code mvn exec:java}
 *
 * <p>Output: {@code thai_output.pdf} written to the working directory.
 */
public class ThaiPdfExample {

    public static void main(String[] args) throws Exception {
        File fontsDir = new File("fonts");
        File noto     = new File(fontsDir, "NotoSansThai-Regular.ttf");
        File tahoma   = new File(fontsDir, "tahoma.ttf");
        File sarabun  = new File(fontsDir, "THSarabunNew.ttf");
        File html     = new File("thai_test.html");
        File output   = new File("thai_output.pdf");

        if (!html.exists()) {
            System.err.println("ERROR: thai_test.html not found.");
            System.err.println("       Run from inside the example/ directory.");
            System.exit(1);
        }
        if (!noto.exists()) {
            System.err.println("ERROR: fonts/NotoSansThai-Regular.ttf not found.");
            System.err.println("       Download from https://fonts.google.com/noto/specimen/Noto+Sans+Thai");
            System.err.println("       and place NotoSansThai-Regular.ttf in the fonts/ subdirectory.");
            System.exit(1);
        }

        System.out.println("Rendering: " + html.getAbsolutePath());
        System.out.println("Output:    " + output.getAbsolutePath());

        try (OutputStream os = new FileOutputStream(output)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.withFile(html);
            builder.toStream(os);

            try (ShapedPdfBoxRenderer renderer = ShapedPdfBoxRenderer.build(builder)) {

                // Noto Sans Thai — modern OpenType, upem 1000 (required)
                // Pass true to enable Sara Am decompose for correct tone-over-nikhahit placement.
                renderer.addShapingFont(noto, "Noto Sans Thai", true);
                System.out.println("  registered: Noto Sans Thai (NotoSansThai-Regular.ttf)");

                // Tahoma — modern OpenType, upem 2048 (optional)
                if (tahoma.exists()) {
                    renderer.addShapingFont(tahoma, "Tahoma", true);
                    System.out.println("  registered: Tahoma (tahoma.ttf)");
                } else {
                    System.out.println("  skipped:    Tahoma — fonts/tahoma.ttf not found (optional)");
                }

                // TH Sarabun New — legacy PUA path (optional)
                // No decompose flag: legacy fonts have no GPOS mark anchors; the engine
                // handles placement via glyph substitution instead.
                if (sarabun.exists()) {
                    renderer.addShapingFont(sarabun, "TH Sarabun New");
                    System.out.println("  registered: TH Sarabun New (THSarabunNew.ttf) — legacy PUA path");
                } else {
                    System.out.println("  skipped:    TH Sarabun New — fonts/THSarabunNew.ttf not found (optional)");
                }

                renderer.layout();
                renderer.createPDF();
            }
        }

        System.out.println();
        System.out.println("Done: " + output.getAbsolutePath());
        System.out.println("Open in Adobe Acrobat or Foxit Reader.");
        System.out.println("Try Ctrl+F to search Thai words and copy-paste to verify searchability.");
    }
}
