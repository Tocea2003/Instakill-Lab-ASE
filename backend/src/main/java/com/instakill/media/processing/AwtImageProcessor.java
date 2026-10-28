package com.instakill.media.processing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Scales images down to a maximum width and stamps a watermark in the bottom-right corner. */
@Component
public class AwtImageProcessor implements ImageProcessor {

    private final int maxWidth;
    private final String watermark;

    public AwtImageProcessor(@Value("${media.processing.max-width:300}") int maxWidth,
                             @Value("${media.processing.watermark:Instakill Demo}") String watermark) {
        this.maxWidth = maxWidth;
        this.watermark = watermark;
    }

    @Override
    public byte[] process(byte[] input, String format) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(input));
            if (source == null) {
                throw new IllegalArgumentException("Not a readable image");
            }
            BufferedImage result = watermark(scale(source));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(result, format, out)) {
                throw new IllegalArgumentException("Unsupported image format: " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedImage scale(BufferedImage src) {
        int width = Math.min(src.getWidth(), maxWidth);
        int height = Math.max(1, Math.round((float) src.getHeight() * width / src.getWidth()));
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, width, height, Color.WHITE, null);
        g.dispose();
        return dst;
    }

    private BufferedImage watermark(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, img.getWidth() / 20)));
        FontMetrics fm = g.getFontMetrics();
        int margin = 6;
        int x = Math.max(0, img.getWidth() - fm.stringWidth(watermark) - margin);
        int y = img.getHeight() - margin - fm.getDescent();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g.setColor(Color.BLACK);
        g.drawString(watermark, x + 1, y + 1);
        g.setColor(Color.WHITE);
        g.drawString(watermark, x, y);
        g.dispose();
        return img;
    }
}
