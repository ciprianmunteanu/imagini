package service;

import domain.PixelInfoDto;
import domain.SinglePixelEffect;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import repo.TwoImageRepo;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class EffectService {
    private final TwoImageRepo repo;

    private class PixelValues {
        public int red, green, blue;

        public PixelValues(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public int getTotal() {
            return red + green + blue;
        }
    }

    public EffectService(TwoImageRepo repo) {
        this.repo = repo;
    }

    public Image getGreyscale () {
        return applySinglePixelEffect(pixelInfo -> {
            Color initialColor = pixelInfo.getInitialColor();
            int val = initialColor.getRed() + initialColor.getGreen() + initialColor.getBlue();
            val /= 3;
            return new Color(val, val, val);
        });
    }
    public Image getContrastEdit (int contrast) {
        if(contrast < -160)
            contrast = -160;
        if(contrast > 160)
            contrast = 160;

        double factor = (double)(259 * (contrast + 255)) / (255 * (259 - contrast));
        Function<Integer, Integer> contrastConversion = (initialColorVal -> {
            int res = (int) (factor * (initialColorVal - 128) + 128);
            return clamp(res);
        });

        return applySinglePixelEffect(pixelInfo -> {
            Color initialColor = pixelInfo.getInitialColor();
            Integer red = contrastConversion.apply(initialColor.getRed());
            Integer green = contrastConversion.apply(initialColor.getGreen());
            Integer blue = contrastConversion.apply(initialColor.getBlue());

            return new Color(red, green, blue);
        });
    }

    public Image getGammaCorrection(double gamma) {
        Function<Integer, Integer> formula = val -> {
            double aux = Math.pow(((double) val / 255), gamma) * 255;
            return (int)aux;
        };

        return applySinglePixelEffect(pixelInfo -> {
            Color initialColor = pixelInfo.getInitialColor();
            int red = formula.apply(initialColor.getRed());
            int green = formula.apply(initialColor.getGreen());
            int blue = formula.apply(initialColor.getBlue());

            return new Color(red, green, blue);
        });
    }

    public Image getSubtraction(BufferedImage otherImage) {
        return applySinglePixelEffect(pixelInfo -> {
            Color initialColor = pixelInfo.getInitialColor();
            Color otherColor = new Color(otherImage.getRGB(pixelInfo.getX(), pixelInfo.getY()));
            return new Color(
                    clamp(initialColor.getRed() - otherColor.getRed()),
                    clamp(initialColor.getGreen() - otherColor.getGreen()),
                    clamp(initialColor.getBlue() - otherColor.getBlue())
            );
        });
    }

    public Image getMedianFilter(int windowSize) throws Exception {
        if(windowSize > 10)
            throw new Exception("Window size is too big");

        int removeMargin = 25;

        BufferedImage image = repo.getSourceImage();

        for(int x=0; x<image.getWidth(); ++x) {
            for(int y=0; y<image.getHeight(); ++y) {
                int leftBorder = max(0, x - windowSize);
                int rightBorder = min(image.getWidth()-1, x + windowSize);

                int topBorder = max(0, y - windowSize);
                int bottomBorder = min(image.getHeight() - 1, y + windowSize);

                List<PixelValues> values = new ArrayList<>();

                for(int i=leftBorder; i<=rightBorder; ++i) {
                    for(int j=topBorder; j<=bottomBorder; ++j) {
                        Color color = new Color(image.getRGB(i, j));
                        values.add(new PixelValues(color.getRed(), color.getGreen(), color.getBlue()));
                    }
                }

                values.sort(Comparator.comparingInt(PixelValues::getTotal));

                int minTotal = values.get(0).getTotal();
                int maxTotal = values.get(values.size()-1).getTotal();

                values.removeIf(v -> {
                    int total = v.getTotal();
                    return total - removeMargin < minTotal || total + removeMargin > maxTotal;
                });

                values.sort(Comparator.comparingInt(PixelValues::getTotal));

                if(values.size() >= 2) {
                    PixelValues val = values.get(values.size() / 2);
                    image.setRGB(x, y, new Color(val.red, val.green, val.blue).getRGB());
                }
            }
        }

        repo.setResultImage(image);

        return SwingFXUtils.toFXImage(image, null);
    }

    /**
     * Applies the given effect to each pixel of the image individually. Saves the new image in the repo and returns it.
     * @return the image after the effect was applied.
     */
    private Image applySinglePixelEffect(SinglePixelEffect effect) {
        BufferedImage image = repo.getSourceImage();

        for(int x=0; x<image.getWidth(); ++x) {
            for(int y=0; y<image.getHeight(); ++y) {
                Color c = new Color(image.getRGB(x, y));
                image.setRGB(x, y, effect.apply(new PixelInfoDto(c, x, y)).getRGB());
            }
        }

        repo.setResultImage(image);

        return SwingFXUtils.toFXImage(image, null);
    }

    /**
     * Ensures that the given value is within the interval [0, 255]
     * @param val value to clamp
     * @return a valid color value
     */
    private int clamp(int val) {
        if(val > 255)
            return 255;
        if(val < 0)
            return 0;

        return val;
    }
}
