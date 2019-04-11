package controller;

import com.sun.scenario.effect.Effect;
import domain.EffectType;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import repo.TwoImageRepo;
import service.EffectService;
import service.ImageService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class MainWindowController {
    private ImageService imageService;
    private EffectService effectService;

    @FXML
    public ImageView sourceImageView;
    @FXML
    public ImageView resultImageView;
    @FXML
    public TextField valueInput;
    @FXML
    public ChoiceBox<EffectType> effectSelectBox;

    @FXML
    private void initialize() {
        TwoImageRepo repo = new TwoImageRepo();
        imageService = new ImageService(repo);
        effectService = new EffectService(repo);
        effectSelectBox.getItems().setAll(EffectType.values());
        effectSelectBox.getSelectionModel().select(0);

        valueInput.textProperty().addListener((observable, oldValue, newValue) -> onTextChanged(oldValue, newValue));
        effectSelectBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                apply(newValue);
            } catch (Exception e) {e.printStackTrace();}
        });

    }

    @FXML
    public void loadImage() throws Exception {
        String path = loadImagePopup();
        imageService.loadImage(path);
        sourceImageView.setImage(imageService.getSourceImage());
        apply(effectSelectBox.getSelectionModel().getSelectedItem());
    }

    /**
     * Creates a popup for selecting an image file
     * @return the path to the file selected by the user
     */
    private String loadImagePopup () throws Exception {
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Images", "jpg", "png");
        chooser.setFileFilter(filter);
        int returnVal = chooser.showOpenDialog(null);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getAbsolutePath().replaceAll("\\\\", "/");
        }

        throw new Exception("JFileChooser returned " + returnVal);
    }

    @FXML
    public void saveImage() {
        imageService.saveImage();
    }

    @FXML
    public void useResultAsSource() {
        sourceImageView.setImage(imageService.useResultAsSource());
    }

    public void apply(EffectType effectType) throws Exception {
        Image newImage;
        if(effectType == EffectType.GREYSCALE)
            newImage = effectService.getGreyscale();
        else if(effectType == EffectType.CONTRAST)
        {
            int value = getIntValue();
            newImage = effectService.getContrastEdit(value);
        }
        else if (effectType == EffectType.GAMMA_CORRECTION) {
            double value = getDoubleValue();
            newImage = effectService.getGammaCorrection(value);
        }
        else if(effectType == EffectType.SUBTRACTION) {
            newImage = effectService.getSubtraction(imageService.loadSeparateImage(loadImagePopup()));
        }
        else if(effectType == EffectType.MEDIAN_FILTER) {
            newImage = effectService.getMedianFilter(getIntValue());
        }
        else
            throw new Exception("Unrecognised effect selected");

        resultImageView.setImage(newImage);
    }

    private int getIntValue() {
        try {
            Double d = Double.parseDouble(valueInput.getText());
            return d.intValue();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double getDoubleValue() {
        try {
            return Double.parseDouble(valueInput.getText());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Called every time the text in the text box changes. Assures that only numbers can be written in the box.
     * @param oldValue the text before the change
     * @param newValue the text after the change
     */
    private void onTextChanged(String oldValue, String newValue) {
        if(!newValue.equals("") && !newValue.equals("-"))
        {
            try {
                Double.parseDouble(newValue);
                apply(effectSelectBox.getSelectionModel().getSelectedItem());
            }
            catch (NumberFormatException ex) {
                valueInput.setText(oldValue);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
