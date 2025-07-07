package de.papenhagen;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

public class PDFHelper {


    private static final Logger LOGGER = Logger.getLogger(PDFHelper.class.getName());

    /**
     * this metod try to check if these PDFs are only scanned images.
     * WARNING: this is far from perfect
     *
     * @param path to the PDF
     * @return ture if the PDF only contains images else, it is a normal txt base PDF.
     */
    public static boolean isImageAsPage(final Path path) {
        int numberOfImage = 0;
        try {
            final PDDocument doc = Loader.loadPDF(path.toFile());

            final int numberOfPages = doc.getNumberOfPages();
            for (final PDPage page : doc.getPages()) {
                final PDResources resource = page.getResources();
                for (final COSName xObjectName : resource.getXObjectNames()) {
                    final PDXObject xObject = resource.getXObject(xObjectName);
                    if (xObject instanceof PDImageXObject) {
                        ((PDImageXObject) xObject).getImage();
                        numberOfImage++;
                    }
                }

            }
            doc.close();

            // PDF pages if equal to the count images
            return numberOfImage == numberOfPages;

        } catch (IOException ex) {
            LOGGER.severe("Exception on image check of given PDF: " + ex.getLocalizedMessage());
            return false;
        }
    }

    public static String getText(final Path path) {
        final PDDocument doc;
        try {
            doc = Loader.loadPDF(path.toFile());
            return new PDFTextStripper().getText(doc);
        } catch (IOException ex) {
            LOGGER.severe("Exception on reading the text from the given PDF: " + ex.getLocalizedMessage());
            return null;
        }
    }

}
