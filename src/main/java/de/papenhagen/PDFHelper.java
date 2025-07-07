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
     * this method try to check if the given PDFs are only scanned images or easier to extract text.
     * WARNING: this is far from perfect
     *
     * @param path to the PDF
     * @return ture if the PDF only contains images else, it is a normal txt base PDF.
     */
    public static boolean isPDFbyOCR(final Path path) {
        int numberOfImage = 0;
        try {
            final PDDocument doc = Loader.loadPDF(path.toFile());
            if (doc.isEncrypted()) {
                LOGGER.severe("Document is encrypted.");
                return false;
            }

            // check amount of images and pages are listed
            // PDF pages are equal to the count images, then we have OCR the PDF.
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

            if (numberOfImage == numberOfPages) {
                doc.close();
                return true;
            }

            //try to extract text
            PDFTextStripper pdfStripper = new PDFTextStripper();
            boolean emptyText = pdfStripper.getText(doc).isEmpty();

            doc.close();

            return emptyText;
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
