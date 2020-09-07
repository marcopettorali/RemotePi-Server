import javafx.application.Platform;

import java.awt.*;
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

    int port;

    final double BASE_MARGIN = 0.0035;
    final double MAX_MARGIN = 0.01;
    final int TIME_MARGIN = 300;

    final int BUFFER_SIZE = 1024 * 1024;

    double y_axis_min;
    double y_axis_max;

    double z_axis_min;
    double z_axis_max;

    int screen_width;
    int screen_height;

    Robot mouse;

    int[] x_window;
    int[] y_window;
    int window_dim = 20;

    int prev_x = 0;
    int prev_y = 0;

    double margin = BASE_MARGIN;

    boolean offset_on = false;
    boolean bl_received = false;
    boolean tr_received = false;
    DatagramSocket serverSocket;

    public Server(int port) {
        super();
        this.port = port;
        try {
            serverSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private long normalizeZCoordinate(long z, long zAxisMin, long zAxisMax) {
        if (zAxisMin > zAxisMax) {
            return z;
        } else {
            // We map each positive value to the right of -1/1 point in the compass circle
            // to a virtual point on the circle as there was not discontinuity.
            // So 1 is mapped to -1, 0.99 is mapped to -1.01, and so on:
            // the mapping function is trivially y = x-2
            return z - 2;
        }
    }

    public void run() {
        try {
            mouse = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        screen_width = screenSize.width;
        screen_height = screenSize.height;
        try {
            byte[] receiveData = new byte[BUFFER_SIZE];


            Socket s = null;
            try {
                s = new Socket("8.8.8.8", 53);
                System.out.println("Listening on udp:" + s.getLocalAddress() + ":" + port);
                Main.generateQRCodeImage(s.getLocalAddress() + ":" + port);
            } catch (IOException e) {
                try {
                    System.out.println(("No internet: impossible to determine my local address\nApproximative address: udp:" + InetAddress.getLocalHost().getHostName() + ":" + port));
                } catch (UnknownHostException unknownHostException) {
                    unknownHostException.printStackTrace();
                }
            }

            DatagramPacket receivePacket = new DatagramPacket(receiveData, BUFFER_SIZE);

            boolean exit = false;

            double y_axis_val = 0;
            double z_axis_val = 0;

            x_window = new int[window_dim];
            y_window = new int[window_dim];
            int window_pointer = 0;

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
                }
                String y_axis = "";
                String z_axis = "";

                boolean z_axis_mode = false;
                boolean bottom_left_mode = false;
                boolean top_right_mode = false;

                for (int i = 0; i < sentence.length(); i++) {
                    char c = sentence.charAt(i);

                    if (c == 'B') {
                        bottom_left_mode = true;
                        continue;
                    }

                    if (c == 'T') {
                        top_right_mode = true;
                        continue;
                    }

                    if (c == 'Y') {
                        continue;
                    }
                    if (c == 'C') {
                        z_axis_mode = true;
                        continue;
                    }

                    if (z_axis_mode) {
                        z_axis += c;
                    } else {
                        y_axis += c;
                    }

                }

                if (exit) {
                    exit = false;
                    continue;
                }

                try {
                    y_axis_val = Double.parseDouble(y_axis);
                } catch (Exception e) {
                }

                try {
                    z_axis_val = Double.parseDouble(z_axis);
                } catch (Exception e) {
                }

                if (bottom_left_mode) {
                    y_axis_min = y_axis_val;
                    z_axis_min = z_axis_val;

                    bl_received = true;

                    if (tr_received) {
                        tr_received = false;

                        if (z_axis_min * z_axis_max < 0 && Math.abs(z_axis_min - z_axis_max) > 1) {
                            offset_on = true;
                            if (z_axis_min < 0) {
                                z_axis_min += 1;
                            } else {
                                z_axis_min -= 1;
                            }

                            if (z_axis_max < 0) {
                                z_axis_max += 1;
                            } else {
                                z_axis_max -= 1;
                            }

                        }
                    }

                } else if (top_right_mode) {
                    y_axis_max = y_axis_val;
                    z_axis_max = z_axis_val;

                    tr_received = true;

                    if (bl_received) {
                        bl_received = false;

                        if (z_axis_min * z_axis_max < 0 && Math.abs(z_axis_min - z_axis_max) > 1) {
                            offset_on = true;
                            if (z_axis_min < 0) {
                                z_axis_min += 1;
                            } else {
                                z_axis_min -= 1;
                            }

                            if (z_axis_max < 0) {
                                z_axis_max += 1;
                            } else {
                                z_axis_max -= 1;
                            }

                        }
                    }

                }

                //-----------------------------------------//
                if (offset_on) {
                    if (z_axis_val < 0) {
                        z_axis_val += 1;
                    } else {
                        z_axis_val -= 1;
                    }
                }

                int x_ = (int) Math.round(screen_width * (z_axis_val - z_axis_min) / (z_axis_max - z_axis_min));
                int y_ = (int) Math.round(screen_height * (y_axis_val - y_axis_max) / (y_axis_min - y_axis_max));

                //System.out.println(Math.round(100.0 * y_axis_val) / 100.0 + ";" + Math.round(100.0 * z_axis_val) / 100.0);
                x_window[window_pointer] = x_;
                y_window[window_pointer] = y_;

                window_pointer++;
                if (window_pointer == window_dim) {
                    int x = 0, y = 0;
                    for (int p = 0; p < window_dim; p++) {
                        x += x_window[p];
                        y += y_window[p];
                    }
                    x = x / window_dim;
                    y = y / window_dim;

                    if (Math.abs(x - prev_x) > margin * screen_width && Math.abs(y - prev_y) > margin * screen_height) {

                        mouse.mouseMove(x, y);
                        prev_x = x;
                        prev_y = y;
                    }

                    window_pointer = 0;

                }
            }
        } catch (IOException e) {
            System.out.println(e);
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

    private void setScreenReferences(String position) {

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
}
