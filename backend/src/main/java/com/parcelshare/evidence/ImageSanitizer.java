package com.parcelshare.evidence;

import com.parcelshare.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Turns an uploaded file into a clean, small JPEG. Decoding and re-encoding means nothing from the original
 * file survives except pixels: EXIF (GPS, device info), embedded scripts and polyglot tricks are all dropped.
 */
public final class ImageSanitizer {

    static final int MAX_SIDE = 1600;
    static final long MAX_PIXELS = 40_000_000L; // refuse decompression bombs before decoding
    static final float QUALITY = 0.82f;

    private ImageSanitizer() {
    }

    public static byte[] toCleanJpeg(byte[] in) {
        if (in == null || in.length < 16 || !(isJpeg(in) || isPng(in))) {
            throw ApiException.bad("Only JPEG or PNG photos are accepted");
        }
        BufferedImage src;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(in))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw ApiException.bad("That file is not a readable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw ApiException.bad("Image dimensions are too large");
                }
                src = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw ApiException.bad("That file is not a readable image");
        }
        if (src == null) {
            throw ApiException.bad("That file is not a readable image");
        }

        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE); // flattens PNG transparency
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return encodeJpeg(out);
    }

    private static byte[] encodeJpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param); // null metadata => no EXIF written
            ios.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw ApiException.bad("Could not process the image");
        } finally {
            writer.dispose();
        }
    }

    static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] b) {
        return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }
}
