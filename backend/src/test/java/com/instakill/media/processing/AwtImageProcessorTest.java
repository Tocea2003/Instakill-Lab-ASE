package com.instakill.media.processing;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwtImageProcessorTest {

    private final AwtImageProcessor processor = new AwtImageProcessor(300, "Instakill Demo");

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void scalesWideImageToMaxWidthKeepingAspectRatio() throws Exception {
        BufferedImage result = read(processor.process(png(1200, 600), "png"));
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(150);
    }

    @Test
    void doesNotUpscaleSmallImages() throws Exception {
        BufferedImage result = read(processor.process(png(100, 80), "png"));
        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(80);
    }

    @Test
    void drawsWatermarkInBottomRightCorner() throws Exception {
        BufferedImage result = read(processor.process(png(300, 200), "png"));
        int blue = Color.BLUE.getRGB();
        boolean changed = false;
        for (int x = 150; x < 300 && !changed; x++) {
            for (int y = 160; y < 200; y++) {
                if (result.getRGB(x, y) != blue) {
                    changed = true;
                    break;
                }
            }
        }
        assertThat(changed).isTrue();
        assertThat(result.getRGB(5, 5)).isEqualTo(blue);
    }

    @Test
    void rejectsBytesThatAreNotAnImage() {
        assertThatThrownBy(() -> processor.process(new byte[]{1, 2, 3}, "png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
