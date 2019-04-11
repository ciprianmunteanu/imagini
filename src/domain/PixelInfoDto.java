package domain;

import java.awt.*;

public class PixelInfoDto {
    private Color initialColor;
    private int x;
    private int y;

    public PixelInfoDto(Color initialColor, int x, int y) {
        this.initialColor = initialColor;
        this.x = x;
        this.y = y;
    }

    public PixelInfoDto(Color initialColor) {
        this.initialColor = initialColor;
    }

    public Color getInitialColor() {
        return initialColor;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
