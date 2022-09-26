/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app;

import java.sql.SQLException;
import java.util.ResourceBundle;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import timber.log.Timber;
import uz.yt.ofd.acr.sample.app.gui.TestFrameController;

/**
 * @author administrator
 */
public class Application {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("version");

    public static final String VERSION = BUNDLE.getString("version").replace("-SNAPSHOT", "");

    public static void main(String[] args) throws SQLException {
        Application app = new Application();
        app.init(args);
        app.run();
    }

    protected void init(String[] args) {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // If Nimbus is not available, you can set the GUI to another look and feel.
        }
    }

    protected void run() throws SQLException {
        System.out.println("version " + VERSION);
        TestFrameController tfc = new TestFrameController(VERSION);
        tfc.show();
    }
}
