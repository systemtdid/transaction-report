package com.tdid.txreport.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.tdid.txreport.config.FontProperties;
import io.github.de8tech.thaipdf.shaping.openhtml.ShapedPdfBoxRenderer;

import jakarta.annotation.PostConstruct;

@Service
public class PdfRenderService {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderService.class);

    private final FontProperties fontProps;
    private File notoFile;
    private File tahomaFile;
    private File sarabunFile;

    public PdfRenderService(FontProperties fontProps) {
        this.fontProps = fontProps;
    }

    @PostConstruct
    void initFonts() {
        notoFile = resolveFont(fontProps.getNoto(), "Noto Sans Thai");
        tahomaFile = resolveFont(fontProps.getTahoma(), "Tahoma");
        sarabunFile = resolveFont(fontProps.getSarabun(), "TH Sarabun New");

        if (notoFile == null && tahomaFile == null) {
            log.warn("No Thai-capable fonts configured. PDF output may not render Thai text correctly. " +
                     "Set FONT_NOTO or FONT_TAHOMA environment variables pointing to TTF files.");
        }
    }

    private File resolveFont(String path, String label) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (!f.exists()) {
            log.warn("Font '{}' not found at path: {}", label, path);
            return null;
        }
        log.info("Font registered: {} ({})", label, f.getAbsolutePath());
        return f;
    }

    public byte[] htmlToPdf(String html) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.withHtmlContent(html, null);
            builder.toStream(bos);

            try (ShapedPdfBoxRenderer renderer = ShapedPdfBoxRenderer.build(builder)) {
                if (notoFile != null) {
                    renderer.addShapingFont(notoFile, "Noto Sans Thai", true);
                }
                if (tahomaFile != null) {
                    renderer.addShapingFont(tahomaFile, "Tahoma", true);
                }
                if (sarabunFile != null) {
                    renderer.addShapingFont(sarabunFile, "TH Sarabun New");
                }
                renderer.layout();
                renderer.createPDF();
            }

            return bos.toByteArray();
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException("PDF rendering failed", e);
        }
    }
}
