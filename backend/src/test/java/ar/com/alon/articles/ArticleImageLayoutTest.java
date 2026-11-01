package ar.com.alon.articles;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ArticleImageLayoutTest {
    private BufferedImage solid(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    @Test
    void reducesWidePhotosWithoutCroppingOrDistortion() {
        var image = ArticleImageStorage.fitForCatalog(solid(2400, 1200));
        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(600);
        assertThat(image.getRGB(0, 0)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(1199, 599)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void preservesPortraitsWithoutUpscalingSmallImages() {
        var image = ArticleImageStorage.fitForCatalog(solid(100, 200));
        assertThat(image.getWidth()).isEqualTo(100);
        assertThat(image.getHeight()).isEqualTo(200);
        assertThat(image.getRGB(0, 0)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(99, 199)).isEqualTo(Color.RED.getRGB());
    }

    @Test
    void compositesTransparencyOverWhite() {
        var source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(10, 10, Color.RED.getRGB());
        var image = ArticleImageStorage.fitForCatalog(source);
        assertThat(image.getRGB(0, 0)).isEqualTo(Color.WHITE.getRGB());
        assertThat(image.getRGB(10, 10)).isEqualTo(Color.RED.getRGB());
    }
}
