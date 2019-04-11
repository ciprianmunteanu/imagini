package domain;

import java.awt.*;

public interface SinglePixelEffect {
    Color apply(PixelInfoDto pixelInfo);
}
