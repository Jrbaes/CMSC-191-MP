package graphtheory;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.Date;

public class Main {

    public static void main(String[] args) throws Exception {

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Date date = new Date();
        new Canvas("Diadikt.io", 800, 600, Color.WHITE);

    }
}
