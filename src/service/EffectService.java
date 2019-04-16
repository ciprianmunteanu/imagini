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

    public Image scale(double ratio) {
        if(ratio < 1)
            return scaleNegatively(ratio);
        return scalePositively(ratio);
    }

    private Image scalePositively(double ratio) {
        BufferedImage originalImage = repo.getSourceImage();

        int resX = (int)(originalImage.getWidth()*ratio)+1;
        int resY = (int)(originalImage.getHeight()*ratio)+1;

        BufferedImage resultImage = new BufferedImage(resX, resY, originalImage.getType());

        double overflowX = 0, overflowY = 0;
        int nrToInterpolateY = 0;
        int x, y=0;

        for(int originalY = 0; originalY<originalImage.getHeight(); ++originalY) {
            x=0;
            for(int originalX = 0; originalX<originalImage.getWidth(); ++originalX) {
                overflowX += ratio;

                int nrToInterpolate = (int)(overflowX-1);
                overflowX -= nrToInterpolate;

                int leftRgb = 0;
                if(originalX != 0) {
                    leftRgb = originalImage.getRGB(originalX-1, originalY);
                }

                Color leftColor = new Color(leftRgb);
                Color rightColor = new Color(originalImage.getRGB(originalX, originalY));

                for(int i=0; i<nrToInterpolate; ++i) {
                    double lw = ((double)i+1)/(nrToInterpolate+1); // left weight
                    double rw = 1-lw; // right weight

                    int red = (int)(leftColor.getRed() * lw + rightColor.getRed() * rw);
                    int green = (int) (leftColor.getGreen() * lw + rightColor.getGreen() * rw);
                    int blue = (int) (leftColor.getBlue() * lw + rightColor.getBlue() * rw);

                    resultImage.setRGB(x, y, new Color(red, green, blue).getRGB());
                    ++x;
                }
                resultImage.setRGB(x, y, originalImage.getRGB(originalX, originalY));
                ++x;
                --overflowX;
            }

            for(int i=0; i<nrToInterpolateY; ++i) {
                double tw = ((double)i+1)/(nrToInterpolateY+1); // top weight
                double bw = 1-tw; // bottom weight

                for(int j=0; j<resX; ++j) {
                    int topRgb = 0;
                    if(y != 0) {
                        topRgb = resultImage.getRGB(j, y-nrToInterpolateY-1);
                    }
                    Color topColor = new Color(topRgb);
                    Color bottomColor = new Color(resultImage.getRGB(j, y));

                    int red = (int)(topColor.getRed() * tw + bottomColor.getRed() * bw);
                    int green = (int)(topColor.getGreen() * tw + bottomColor.getGreen() * bw);
                    int blue = (int)(topColor.getBlue() * tw + bottomColor.getBlue() * bw);

                    resultImage.setRGB(j, y - nrToInterpolateY + i, new Color(red, green, blue).getRGB());
                }
            }

            overflowY += ratio;
            nrToInterpolateY = (int)overflowY-1;
            overflowY -= nrToInterpolateY + 1;
            y += nrToInterpolateY + 1;
        }

        repo.setResultImage(resultImage);

        return SwingFXUtils.toFXImage(resultImage, null);
    }

    private Image scaleNegatively(double ratio) {
        double invRatio = 1/ratio;
        BufferedImage originalImage = repo.getSourceImage();

        int resX = (int)(originalImage.getWidth()*ratio)-1;
        int resY = (int)(originalImage.getHeight()*ratio)-1;

        BufferedImage resultImage = new BufferedImage(resX, resY, originalImage.getType());

        for(int x=0; x<resX; ++x) {
            for(int y=0; y<resY; ++y) {
                int originalX = (int)(x * invRatio);
                int originalY = (int)(y * invRatio);
                resultImage.setRGB(x, y, originalImage.getRGB(originalX, originalY));
            }
        }

        repo.setResultImage(resultImage);

        return SwingFXUtils.toFXImage(resultImage, null);
    }

    public Image getMedianFilter(int windowSize) throws Exception {
        if(windowSize > 10)
            throw new Exception("Window size is too big");
        if(windowSize < 1)
            throw new Exception("Window size is too small");

        int removeMargin = 15;

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
                        if(x == i && y == j)
                            continue;
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
