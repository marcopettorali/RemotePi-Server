import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class Server extends Thread {

    //commands
    final private String YOUTUBE_REQUEST_MSG = "YT";
    final private String BROWSER_REQUEST_MSG = "BW";
    final private String HIDE_QR_MSG = "HQ";
    final private String PING_MSG = "PI";

    //mouse constants
    final private String MOUSE_BUTTON_HEADER = "BT";
    final private String MOUSE_WHEEL_HEADER = "WH";
    final private String MOUSE_POSITION_HEADER = "PO";
    final private String MOUSE_SCREEN_REF_HEADER = "SR";
    final private String TEXT_HEADER = "TX";

    int port;

    final double BASE_MARGIN = 0.0035;
    final double MAX_MARGIN = 0.01;
    final int TIME_MARGIN = 300;

    final int BUFFER_SIZE = 1024 * 1024;

    double yAxisBottom;
    double yAxisTop;

    double zAxisLeft;
    double zAxisRight;

    int SCREEN_WIDTH;
    int SCREEN_HEIGHT;

    Robot mouse;

    int[] xScreenMovingWindow;
    int[] yScreenMovingWindow;
    int screenMovingWindowPointer;
    int screenMovingWindowSize = 20;

    int previousXPoint = 0;
    int previousYPoint = 0;

    double margin = BASE_MARGIN;

    boolean bottomLeftCornerReceived = false;
    boolean topRightCornerReceived = false;
    DatagramSocket serverSocket;

    public Server(int port) throws Exception {
        super();

        // setup server socket
        this.port = port;
        try {
            serverSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // extract current position
        Socket internetSocket = null;
        try {
            internetSocket = new Socket("8.8.8.8", 53);
            System.out.println("Listening on udp:" + internetSocket.getLocalAddress() + ":" + port);
            Main.generateQRCodeImage(internetSocket.getLocalAddress() + ":" + port);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "No internet connection. Retry later.", "RemotePi Server", JOptionPane.ERROR_MESSAGE);
            throw new Exception("No internet connection. Retry later.");
        }

        //instantiate mouse
        try {
            mouse = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }

        //get screen's dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        SCREEN_WIDTH = screenSize.width;
        SCREEN_HEIGHT = screenSize.height;

        //initialize the samples' moving window
        xScreenMovingWindow = new int[screenMovingWindowSize];
        yScreenMovingWindow = new int[screenMovingWindowSize];
        screenMovingWindowPointer = 0;

    }

    public void run() {
        try {
            byte[] receiveData = new byte[BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, BUFFER_SIZE);

            while (true) {
                serverSocket.receive(receivePacket);
                String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());

                String command = sentence.substring(0, 2);
                System.out.println(command);
                String payload;
                switch (command) {
                    case PING_MSG:
                        answerPing(receivePacket.getAddress());
                        break;
                    case HIDE_QR_MSG:
                        //Main.hideQRFrame();
                        break;
                    case YOUTUBE_REQUEST_MSG:
                        payload = sentence.substring(2);
                        handleYoutubeRequest(payload);
                        break;
                    case BROWSER_REQUEST_MSG:
                        payload = sentence.substring(2);
                        handleBrowserRequest(payload);
                        break;
                    case MOUSE_SCREEN_REF_HEADER:
                        payload = sentence.substring(2);
                        setScreenReferences(payload);
                        break;
                    case MOUSE_BUTTON_HEADER:
                        payload = sentence.substring(2);
                        handleMouseButton(payload);
                        break;
                    case MOUSE_WHEEL_HEADER:
                        payload = sentence.substring(2);
                        handleMouseWheel(payload);
                        break;
                    case MOUSE_POSITION_HEADER:
                        payload = sentence.substring(2);
                        handleMousePosition(payload);
                        break;
                    case TEXT_HEADER:
                        payload = sentence.substring(2);
                        handleReceivedText(payload);
                        break;
                }

            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private void handleReceivedText(String payload) {
        StringSelection stringSelection = new StringSelection(payload);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private double normalizeZCoordinate(double z, double zAxisMin, double zAxisMax) {
        if (zAxisMin > zAxisMax) {
            return z;
        } else if (z > 0) {
            // We map each positive value to the right of -1/1 point in the compass circle
            // to a virtual point on the circle as there was not discontinuity.
            // So 1 is mapped to -1, 0.99 is mapped to -1.01, and so on:
            // the mapping function is trivially y = x-2
            return z - 2;
        } else {
            return z;
        }
    }

    private void handleMousePosition(String position) {
        double[] coordinates = extractCoordinates(position);
        double yVector = coordinates[0];
        double zVector = normalizeZCoordinate(coordinates[1], zAxisLeft, zAxisRight);

        double zVectorRightNormalized = normalizeZCoordinate(zAxisRight, zAxisLeft, zAxisRight);

        int xSample = (int) Math.round(SCREEN_WIDTH * (zVector - zAxisLeft) / (zVectorRightNormalized - zAxisLeft));
        int ySample = (int) Math.round(SCREEN_HEIGHT * (yVector - yAxisTop) / (yAxisBottom - yAxisTop));

        xScreenMovingWindow[screenMovingWindowPointer] = xSample;
        yScreenMovingWindow[screenMovingWindowPointer] = ySample;

        screenMovingWindowPointer++;
        if (screenMovingWindowPointer == screenMovingWindowSize) {
            int currentXPoint = 0;
            int currentYPoint = 0;

            for (int i = 0; i < screenMovingWindowSize; i++) {
                currentXPoint += xScreenMovingWindow[i];
                currentYPoint += yScreenMovingWindow[i];
            }
            currentXPoint /= screenMovingWindowSize;
            currentYPoint /= screenMovingWindowSize;

            if (Math.abs(currentXPoint - previousXPoint) > margin * SCREEN_WIDTH && Math.abs(currentYPoint - previousYPoint) > margin * SCREEN_HEIGHT) {
                mouse.mouseMove(currentXPoint, currentYPoint);
                previousXPoint = currentXPoint;
                previousYPoint = currentYPoint;
            }
            screenMovingWindowPointer = 0;
        }
    }

    private void handleMouseWheel(String action) {
        if (action.charAt(0) == 'D') {
            mouse.mouseWheel(3);

        } else if (action.charAt(0) == 'U') {
            mouse.mouseWheel(-3);
        }
    }

    private void handleMouseButton(String button) {
        int buttonMask;
        char buttonCode = button.charAt(0);
        char action = button.charAt(1);
        switch (buttonCode) {
            case 'L':
                buttonMask = InputEvent.BUTTON1_DOWN_MASK;
                break;
            case 'R':
                buttonMask = InputEvent.BUTTON3_DOWN_MASK;
                break;
            default:
                buttonMask = -1;
                break;
        }

        if (action == 'D') {
            mouse.mousePress(buttonMask);
            margin = MAX_MARGIN;

        } else if (action == 'U') {
            mouse.mouseRelease(buttonMask);
            new Thread() {
                public void run() {
                    try {
                        Thread.sleep(TIME_MARGIN);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    margin = BASE_MARGIN;
                }
            }.start();
        }
    }

    private void setScreenReferences(String payload) {
        char mode = payload.charAt(0);
        String position = payload.substring(1);
        double[] coordinates = extractCoordinates(position);
        double y = coordinates[0];
        double z = coordinates[1];
        if (mode == 'B') {
            yAxisBottom = y;
            zAxisLeft = z;
            bottomLeftCornerReceived = true;
        } else if (mode == 'T') {
            yAxisTop = y;
            zAxisRight = z;
            topRightCornerReceived = true;
        }
    }

    private void answerPing(InetAddress address) {
        DatagramPacket pack = new DatagramPacket("PI".getBytes(), 2);
        pack.setAddress(address);
        try {
            serverSocket.send(pack);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void handleYoutubeRequest(String search) {
        System.out.println(search);
        ProcessBuilder pb = new ProcessBuilder(Main.BROWSER_PATH, "https://www.youtube.com/results?search_query=" + search);
        pb.directory(new File("."));
        try {
            pb.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleBrowserRequest(String search) {
        if (search.contains(" ") || !search.contains(".")) {
            search = "? " + search;
        }
        ProcessBuilder pb = new ProcessBuilder(Main.BROWSER_PATH, search.trim());
        pb.directory(new File("."));
        try {
            pb.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double[] extractCoordinates(String payload) {
        String yCoordinateStr = "";
        String zCoordinateStr = "";
        boolean zMode = false;

        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (c == 'C') {
                zMode = true;
                continue;
            } else if (c == 'Y') {
                zMode = false;
                continue;
            }

            if (!zMode) {
                yCoordinateStr += c;
            } else {
                zCoordinateStr += c;
            }
        }
        try {
            double[] ret = new double[2];
            ret[0] = Double.parseDouble(yCoordinateStr);
            ret[1] = Double.parseDouble(zCoordinateStr);
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}