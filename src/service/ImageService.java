package service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import repo.TwoImageRepo;

import java.awt.image.BufferedImage;


public class ImageService {
    private final TwoImageRepo repo;

    public ImageService(TwoImageRepo repo) {
        this.repo = repo;
    }

    public Image getSourceImage() {
        return SwingFXUtils.toFXImage(repo.getSourceImage(), null);
    }

    public void saveImage() {
        repo.save();
    }

    public Image loadImage(String path) {
        return SwingFXUtils.toFXImage(repo.loadImage(path), null);
    }

    public BufferedImage loadSeparateImage(String path) {
        return repo.loadSeparateImage(path);
    }

    public Image useResultAsSource() {
        repo.setSourceToResult();
        return SwingFXUtils.toFXImage(repo.getSourceImage(), null);
    }
}
