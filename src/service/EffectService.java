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

    public Image contour(int cutoff) {
        BufferedImage resultImage = getCountourAux(cutoff);

        repo.setResultImage(resultImage);
        return SwingFXUtils.toFXImage(resultImage, null);
    }

    private BufferedImage getCountourAux(int cutoff) {
        BufferedImage image = repo.getSourceImage();

        // a pixel is part of a margin if there is at least one neighbour pixel that is brighter by *cutoff*

        cutoff *= 3; // so we dont't have to use divisions later

        BufferedImage resultImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

        for(int x = 1; x<image.getWidth()-1; ++x) { // we skip the border for simplicity
            for(int y=1; y<image.getHeight()-1; ++y) {
                Color c = new Color(image.getRGB(x, y));
                int val = c.getRed() + c.getBlue() + c.getGreen();

                boolean isMargin = false;

                Color c1 = new Color(image.getRGB(x+1, y));
                int val1 = c1.getRed() + c1.getGreen() + c1.getBlue();
                if(val1 - val > cutoff)
                    isMargin = true;

                Color c2 = new Color(image.getRGB(x-1, y));
                int val2 = c2.getRed() + c2.getGreen() + c2.getBlue();
                if(val2 - val > cutoff)
                    isMargin = true;

                Color c3 = new Color(image.getRGB(x, y+1));
                int val3 = c3.getRed() + c3.getGreen() + c3.getBlue();
                if(val3 - val > cutoff)
                    isMargin = true;

                Color c4 = new Color(image.getRGB(x, y-1));
                int val4 = c4.getRed() + c4.getGreen() + c4.getBlue();
                if(val4 - val > cutoff)
                    isMargin = true;

                if(isMargin)
                    resultImage.setRGB(x, y, Color.black.getRGB());
                else
                    resultImage.setRGB(x, y, Color.white.getRGB());
            }
        }

        for(int x=0; x<image.getWidth(); ++x)
        {
            resultImage.setRGB(x, 0, Color.white.getRGB());
            resultImage.setRGB(x, image.getHeight()-1, Color.white.getRGB());
        }
        for(int y=0; y<image.getHeight(); ++y)
        {
            resultImage.setRGB(0, y, Color.white.getRGB());
            resultImage.setRGB(image.getWidth()-1, y, Color.white.getRGB());
        }

        return resultImage;
    }

    public Image skeleton (int cutoff) {
        BufferedImage image = repo.getSourceImage();

        BufferedImage resultImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

        BufferedImage contourImg = getCountourAux(cutoff);

        for(int x =1; x<image.getWidth()-1; ++x) {
            for(int y=1; y<image.getHeight()-1; ++y) {
                int min = getMinDist(contourImg, x, y);


                if(min <= 0) { // we are outside of the object
                    resultImage.setRGB(x, y, Color.white.getRGB());
                }
                else {
                    int min1 = getMinDist(contourImg, x+1, y);
                    int min2 = getMinDist(contourImg, x-1, y);
                    int min3 = getMinDist(contourImg, x, y+1);
                    int min4 = getMinDist(contourImg, x, y-1);

                    int min5 = getMinDist(contourImg, x+1, y+1);
                    int min6 = getMinDist(contourImg, x+1, y-1);
                    int min7 = getMinDist(contourImg, x-1, y-1);
                    int min8 = getMinDist(contourImg, x-1, y+1);

                    if(min >= min1 && min >= min2 && min >= min3 && min >= min4 && min >= min5 && min >= min6 && min >= min7 && min >= min8)
                    {
                        resultImage.setRGB(x, y, Color.black.getRGB());
                    }
                    else {
                        resultImage.setRGB(x, y, Color.white.getRGB());
                    }
                }
            }
        }

        for(int x=0; x<image.getWidth(); ++x)
        {
            resultImage.setRGB(x, 0, Color.white.getRGB());
            resultImage.setRGB(x, image.getHeight()-1, Color.white.getRGB());
        }
        for(int y=0; y<image.getHeight(); ++y)
        {
            resultImage.setRGB(0, y, Color.white.getRGB());
            resultImage.setRGB(image.getWidth()-1, y, Color.white.getRGB());
        }

        repo.setResultImage(resultImage);
        return SwingFXUtils.toFXImage(resultImage, null);
    }


    private int getMinDist(BufferedImage contourImg, int x, int y) {
        int dist1 = skeletonDist(contourImg, x, y, 1, 0);
        int dist2 = skeletonDist(contourImg, x, y, -1, 0);
        int dist3 = skeletonDist(contourImg, x, y, 0, 1);
        int dist4 = skeletonDist(contourImg, x, y, 0, -1);

        int dist5 = skeletonDist(contourImg, x, y, 1, 1);
        int dist6 = skeletonDist(contourImg, x, y, 1, -1);
        int dist7 = skeletonDist(contourImg, x, y, -1, -1);
        int dist8 = skeletonDist(contourImg, x, y, -1, 1);


        List<Integer> distances = Arrays.asList(dist1, dist2, dist3, dist4, dist5, dist6, dist7, dist8);
        Integer min = distances.stream().min(Integer::compareTo).get();
        return min;
    }

    /**
     * Finds the distance in the given direction (dx, dy)
     * If no object is found in that direction, returns -1
     * Otherwise, returns the distance
     */
    private int skeletonDist(BufferedImage image, int x, int y, int dx, int dy) {
        int dist = 0;
        boolean found = false;
        while (x > 0 && y > 0 && x < image.getWidth() && y < image.getHeight()) {
            Color c = new Color(image.getRGB(x, y));
            if(c.getRed() == 0) // we hit an object
            {
                found = true;
                break;
            }
            x += dx;
            y += dy;
            ++dist;
        }

        if(!found)
            return -1;
        return dist;
    }
    final int BLACK = Color.black.getRGB();


    public Image thinning() {
        // pentru fiecare pixel:
        // daca are cel putin 2 vecini:
        // vezi daca exsta vreo pereche de vecini care depinde de pixel ca sa creeze un drum

        BufferedImage image = repo.getSourceImage();

        for(int x=0; x<image.getWidth(); ++x)
        {
            image.setRGB(x, 0, Color.white.getRGB());
            image.setRGB(x, image.getHeight()-1, Color.white.getRGB());
        }
        for(int y=0; y<image.getHeight(); ++y)
        {
            image.setRGB(0, y, Color.white.getRGB());
            image.setRGB(image.getWidth()-1, y, Color.white.getRGB());
        }


        for(int x=1; x<image.getWidth()-1; ++x) {
            for(int y=1; y<image.getHeight()-1;++y) {
                if(getNrOfNeighbors(image, x, y) >= 2) {
                    if(checkThinningContidions(image, x, y))
                        image.setRGB(x, y, Color.white.getRGB());
                }
            }
        }

        for(int x=image.getWidth()-2; x>0; --x) {
            for(int y=image.getHeight()-2; y>0;--y) {
                if(getNrOfNeighbors(image, x, y) >= 2) {
                    if(checkThinningContidions(image, x, y))
                        image.setRGB(x, y, Color.white.getRGB());
                }
            }
        }

        repo.setResultImage(image);
        return SwingFXUtils.toFXImage(image, null);
    }

    /**
     * @return true if it can be eliminated
     */
    private boolean checkThinningContidions(BufferedImage image, int x, int y) {
        if(image.getRGB(x-1, y) == BLACK && image.getRGB(x+1, y) == BLACK && image.getRGB(x, y-1) != BLACK && image.getRGB(x, y+1) != BLACK)
            return false;
        if(image.getRGB(x, y-1) == BLACK && image.getRGB(x, y+1) == BLACK && image.getRGB(x-1, y) != BLACK && image.getRGB(x+1, y) != BLACK)
            return false;

        if(image.getRGB(x, y-1) == BLACK && image.getRGB(x+1, y) == BLACK && image.getRGB(x+1,y-1) != BLACK)
            return false;
        if(image.getRGB(x, y-1) == BLACK && image.getRGB(x-1, y) == BLACK && image.getRGB(x-1,y-1) != BLACK)
            return false;
        if(image.getRGB(x, y+1) == BLACK && image.getRGB(x+1, y) == BLACK && image.getRGB(x+1,y+1) != BLACK)
            return false;
        if(image.getRGB(x, y+1) == BLACK && image.getRGB(x-1, y) == BLACK && image.getRGB(x-1,y+1) != BLACK)
            return false;

        return true;
    }

    private int getNrOfNeighbors(BufferedImage img, int x, int y) {
        int nr = 0;

        if(img.getRGB(x-1, y) == BLACK)
            ++nr;
        if(img.getRGB(x+1, y) == BLACK)
            ++nr;
        if(img.getRGB(x, y+1) == BLACK)
            ++nr;
        if(img.getRGB(x, y-1) == BLACK)
            ++nr;
        return nr;
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
