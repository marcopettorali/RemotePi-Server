import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.swing.*;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Scanner;

public class Main extends JPanel {

    public static String BROWSER_PATH = "";
    public static JFrame qrFrame;

    public static void generateQRCodeImage(String text) {
        int width = 500;
        int height = 500;
        String filePath = "./address_qr.png";

        text = text.substring(1);

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
            Path path = FileSystems.getDefault().getPath(filePath);
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);
        } catch (Exception e) {
            e.printStackTrace();
        }

        qrFrame = new JFrame();
        ImageIcon icon = new ImageIcon(filePath);
        JLabel label = new JLabel(icon);
        qrFrame.add(label);
        qrFrame.setTitle("RemotePi Server: " + text);
        qrFrame.pack();
        qrFrame.setVisible(true);
    }

    public static void hideQRFrame(){
        qrFrame.setVisible(false);
    }

    private static String getBrowserNameFromDialog() {
        //Create a file chooser
        JFrame frame = new JFrame("RemotePi Server");
        final JFileChooser fc = new JFileChooser();

        File browser = null;
        browser = new File("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");
        if (!browser.exists()) {
            browser = new File("C:\\Program Files\\Mozilla Firefox\\firefox.exe");
            if (!browser.exists()) {
                browser = new File("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
            }
        }

        fc.setCurrentDirectory(browser);
        int returnVal = fc.showOpenDialog(frame);
        frame.pack();
        frame.setVisible(true);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            frame.setVisible(false);
            return file.getPath();
        } else {
            return null;
        }
    }

    public static void main(String[] args) {

        File config = new File("config.txt");
        if (!config.exists()) {
            String infoMessage = "config.txt file not found. Select the location for your preferred browser";
            JOptionPane.showMessageDialog(null, infoMessage, "RemotePi Server - open browser", JOptionPane.WARNING_MESSAGE);

            BROWSER_PATH = getBrowserNameFromDialog();
            if (BROWSER_PATH == null) {
                System.exit(1);
            }

            try {
                DataOutputStream dos = new DataOutputStream(new FileOutputStream(config));
                dos.write(("browser = \"" + BROWSER_PATH + "\"").getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            try {
                Scanner sc = new Scanner(config);
                while (sc.hasNext()) {
                    String sentence = sc.nextLine();
                    String key = sentence.split("=")[0].trim();
                    String value = sentence.split("=")[1].trim();
                    switch (key) {
                        case "browser":
                            BROWSER_PATH = value;
                            break;
                    }
                }
                if (BROWSER_PATH == "") {
                    throw new Exception();
                }
            } catch (Exception e) {
                String infoMessage = "config.txt file corrupted. Select the location for your preferred browser";
                JOptionPane.showMessageDialog(null, infoMessage, "RemotePi Server - open browser", JOptionPane.WARNING_MESSAGE);
                BROWSER_PATH = getBrowserNameFromDialog();
                if (BROWSER_PATH == null) {
                    System.exit(1);
                }

                config.delete();
                try {
                    DataOutputStream dos = new DataOutputStream(new FileOutputStream(config));
                    dos.write(("browser = \"" + BROWSER_PATH + "\"").getBytes());
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

            }
        }


        try {
            new Server(8888).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
