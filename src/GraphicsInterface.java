

/**
 * Created by Michael Haines on 1/1/2015.
 */

import messaging.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.*;

public class GraphicsInterface extends JFrame {

    final serialTest engage = new serialTest();
    final MessageReader messageReader = new MessageReader();
    // final xboxControllerTest connectController = new xboxControllerTest();
    private JComboBox commPorts;
    private JTextArea console;
    private JButton initialize;
    private JLabel batteryVoltage, limitSwitchZero, limitSwitchOne, encoderLeftFront,
            encoderLeftRear,encoderRightFront,encoderRightRear;
    private String[] portList = {};
    private boolean isConnected = false;
    private double voltage;
    private boolean canceldashboard = false;
    SerialWorker initiateController;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);


    public GraphicsInterface(){
        voltage = 0;
        createGraphicInterface();
        updateDashboard();
        canceldashboard = true;
    }

    public void createGraphicInterface(){
        Container GraphicsInterfacePane = getContentPane();
        GraphicsInterfacePane.setLayout(null);
        engage.searchForPorts();
        redirectSystemStreams();


        console = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(console,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        //console.setWrapStyleWord(true);
        console.setEditable(false);
        //console.setRows(80);
        //console.setColumns(60);
        scrollPane.setSize(700,250);
        scrollPane.setLocation(50,280);

        GraphicsInterfacePane.add(scrollPane);
        //GraphicsInterfacePane.add(console);



        commPorts = new JComboBox(engage.getPorts().toArray());
        commPorts.setLocation(250,50);
        commPorts.setSize(100,30);
        //commPorts.setSelectedIndex(0);
        GraphicsInterfacePane.add(commPorts);

        initialize = new JButton();
        initialize.setText("initialize");
        initialize.setSize(130,50);
        initialize.setLocation(50,50);
        initialize.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {

                        if(!isConnected) {
                            engage.setPortname(commPorts.getSelectedItem().toString());
                            engage.initialize();
                            engage.portConnect();
                            initiateController = new SerialWorker();
                            initiateController.execute();
                            initialize.setText("disconnect");
                            isConnected = true;
                            canceldashboard = false;
                            //updateDashboard();

                        }
                        else if (isConnected && initiateController != null) {
                            canceldashboard = true;
                            initiateController.kill();
                            engage.close();
                            initialize.setText("initialize");
                            //scheduler.shutdown();
                            isConnected = false;

                        }
                    }
                }
        );
        GraphicsInterfacePane.add(initialize);

        batteryVoltage = new JLabel();
        batteryVoltage.setText(String.format("Battery Voltage: %s", voltage)+'v');
        batteryVoltage.setSize(140,30);
        batteryVoltage.setLocation(50,120);
        GraphicsInterfacePane.add(batteryVoltage);

        limitSwitchZero = new JLabel();
        limitSwitchZero.setText("Top Limit Switch On: false");
        limitSwitchZero.setSize(200, 30);
        limitSwitchZero.setLocation(50, 150);
        GraphicsInterfacePane.add(limitSwitchZero);

        limitSwitchOne = new JLabel();
        limitSwitchOne.setText("Bottom Limit Switch On: false");
        limitSwitchOne.setSize(200, 30);
        limitSwitchOne.setLocation(50, 170);
        GraphicsInterfacePane.add(limitSwitchOne);

        encoderLeftFront = new JLabel();
        encoderLeftFront.setText("Front Left RPM: 0");
        encoderLeftFront.setSize(150, 30);
        encoderLeftFront.setLocation(270, 120);
        GraphicsInterfacePane.add(encoderLeftFront);

        encoderLeftRear = new JLabel();
        encoderLeftRear.setText("Rear Left RPM: 0");
        encoderLeftRear.setSize(150, 30);
        encoderLeftRear.setLocation(50, 260);
        GraphicsInterfacePane.add(encoderLeftRear);

        encoderRightRear = new JLabel();
        encoderRightRear.setText("Rear Right RPM: 0");
        encoderRightRear.setSize(150, 30);
        encoderRightRear.setLocation(200, 260);
        GraphicsInterfacePane.add(encoderRightRear);

        encoderRightFront = new JLabel();
        encoderRightFront.setText("Front Right RPM: 0");
        encoderRightFront.setSize(150, 30);
        encoderRightFront.setLocation(420, 120);
        GraphicsInterfacePane.add(encoderRightFront);


        setTitle("RoboSmokey 3000 Control Interface");
        setSize(800,600);
        setVisible(true);
    }
    private void updateTextArea(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                console.append(text);
            }
        });
    }


    private void updateDashboard(){


        final Runnable dashboardUpdate = new Runnable() {
            @Override
            public void run() {
                if(canceldashboard) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                else {
                    if (messageReader.messageReady()) {
                        byte[] data = messageReader.getMessage();
                        IMessage msg = MessageParser.parse(data);

                        if (msg instanceof BatteryMessage) {
                            voltage = ((BatteryMessage) msg).getVoltage();
                            batteryVoltage.setText(String.format("Battery Voltage: %s", voltage +'v'));
                        }
                        if (msg instanceof LimitSwitchMessage){
                            if(((LimitSwitchMessage)msg).getLimitSwitchId() == 0  ){
                                limitSwitchZero.setText((String.format("Top Limit Switch on: %s",
                                        ((LimitSwitchMessage)msg).getIsPressed())));
                            }
                            else if(((LimitSwitchMessage)msg).getLimitSwitchId() == 1  ){
                                limitSwitchOne.setText((String.format("Top Limit Switch on: %s",
                                        ((LimitSwitchMessage) msg).getIsPressed())));
                            }
                        }
                        if (msg instanceof EncoderMessage){

                            byte MSB = ((EncoderMessage)msg).getMostSignificantBit();
                            byte LSB = ((EncoderMessage)msg).getLeastSignificantBit();
                            int combined = (MSB << 8 ) | (LSB & 0xff);

                            if(((EncoderMessage)msg).getEncoderMessageId() == 0){
                                encoderLeftFront.setText((String.format("Front Left RPM: %s", combined)));
                            }
                            else if(((EncoderMessage)msg).getEncoderMessageId() == 1){
                                encoderLeftRear.setText((String.format("Front Left RPM: %s", combined)));
                            }
                            else if(((EncoderMessage)msg).getEncoderMessageId() == 2){
                                encoderRightFront.setText((String.format("Front Left RPM: %s", combined)));
                            }
                            else if(((EncoderMessage)msg).getEncoderMessageId() == 3){
                                encoderRightRear.setText((String.format("Front Left RPM: %s", combined)));
                            }

                        }
                    }
                }
            }

        };

        final ScheduledFuture<?> dashboardUpdater =
                scheduler.scheduleAtFixedRate(dashboardUpdate, 20 , 20, TimeUnit.MILLISECONDS);

        /*if(canceldashboard) {
            scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    dashboardUpdater.cancel(true);
                }
            },0,TimeUnit.SECONDS);
        }*/
    }


    private void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                updateTextArea(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                updateTextArea(new String(b, off, len));
            }

            @Override
            public void write(byte[] b) throws IOException {
                write(b, 0, b.length);
            }
        };

        System.setOut(new PrintStream(out, true));
        //System.setErr(new PrintStream(out, true));
    }

    public static void main(String[] args){
        GraphicsInterface graphics = new GraphicsInterface();
        graphics.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
