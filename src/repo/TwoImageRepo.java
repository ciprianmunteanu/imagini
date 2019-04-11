package repo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class TwoImageRepo {
    private BufferedImage sourceImage;
    private BufferedImage resultImage;
    private String crtImagePath;

    /**
     * Loads a new image and sets it as the source image
     * @param path absolute path
     */
    public BufferedImage loadImage(String path) {
        try {
            sourceImage = ImageIO.read(new File(path));
            crtImagePath = path;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sourceImage;
    }

    public BufferedImage loadSeparateImage(String path) {
        try {
            return ImageIO.read(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public BufferedImage getSourceImage() {
        return getCopy(sourceImage);
    }

    public void setResultImage(BufferedImage resultImage) {
        this.resultImage = resultImage;
    }

    public void setSourceToResult() {
        sourceImage = getCopy(resultImage);
    }

    public void save() {
        try {
            File outputFile = new File(crtImagePath);
            ImageIO.write(resultImage, crtImagePath.split("\\.")[1], outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BufferedImage getCopy(BufferedImage img) {
        BufferedImage cpy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        for(int x=0; x<img.getWidth(); ++x) {
            for(int y=0; y<img.getHeight(); ++y) {
                cpy.setRGB(x, y, img.getRGB(x, y));
            }
        }

        return cpy;
    }
}
