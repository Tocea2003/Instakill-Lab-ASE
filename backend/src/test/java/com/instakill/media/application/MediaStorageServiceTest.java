package com.instakill.media.application;

import com.instakill.common.error.BadRequestException;
import com.instakill.common.id.IdGenerator;
import com.instakill.media.processing.AwtImageProcessor;
import com.instakill.media.processing.ImageProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageServiceTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final IdGenerator ids = () -> ID;
    private final ImageProcessor processor = new AwtImageProcessor(300, "Instakill Demo");

    @TempDir
    Path tmp;

    @Test
    void storesFileUnderGeneratedNameAndCreatesDirectory() throws Exception {
        Path dir = tmp.resolve("not-yet-there");
        MediaStorageService service = new MediaStorageService(dir.toString(), ids, processor);

        String url = service.store(new MockMultipartFile("file", "clip.MP4", "video/mp4", new byte[]{1, 2, 3}));

        assertThat(url).isEqualTo("/media/" + ID + ".mp4");
        assertThat(Files.readAllBytes(dir.resolve(ID + ".mp4"))).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsEmptyFile() {
        MediaStorageService service = new MediaStorageService(tmp.toString(), ids, processor);
        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnsupportedOrMissingExtension() {
        MediaStorageService service = new MediaStorageService(tmp.toString(), ids, processor);
        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "noext", "image/png", new byte[]{1})))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void processesPngBeforeSaving() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB), "png", out);
        MediaStorageService service = new MediaStorageService(tmp.toString(), ids, processor);

        service.store(new MockMultipartFile("file", "big.png", "image/png", out.toByteArray()));

        BufferedImage saved = ImageIO.read(tmp.resolve(ID + ".png").toFile());
        assertThat(saved.getWidth()).isEqualTo(300);
        assertThat(saved.getHeight()).isEqualTo(200);
    }

    @Test
    void rejectsImageExtensionWithNonImageBytes() {
        MediaStorageService service = new MediaStorageService(tmp.toString(), ids, processor);
        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2, 3})))
                .isInstanceOf(BadRequestException.class);
    }
}
