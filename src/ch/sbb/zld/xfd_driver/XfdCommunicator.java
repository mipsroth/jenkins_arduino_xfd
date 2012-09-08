package ch.sbb.zld.xfd_driver;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Methods for sending an array of colors to the XFD using USB serial.
 */
public class XfdCommunicator {

    private Properties properties;

    private CommPortIdentifier portIdentifier = null;

    XfdCommunicator(Properties properties) {
        this.properties = properties;
        findPort();
    }

    /**
     * Find a port of type SERIAL and check challenge-response.
     */
    private void findPort() {
        @SuppressWarnings("unchecked")
        Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();
        while (portEnum.hasMoreElements()) {
            CommPortIdentifier candidatePort = portEnum.nextElement();
            if (candidatePort.getPortType() == CommPortIdentifier.PORT_SERIAL && challengeResponse(candidatePort)) {
                portIdentifier = candidatePort;
            }
        }
        if (portIdentifier != null) {
            System.out.println("using port " + portIdentifier.getName());
        } else {
            System.out.println("serial port not found");
            System.exit(-1);
        }
    }

    /**
     * Send "?" to the serial port and expect "!xfd*" response!
     */
    private boolean challengeResponse(CommPortIdentifier candidatePort) {
        if (candidatePort.isCurrentlyOwned()) {
            // port in use by other software
            return false;
        }
        
        boolean responseOk = false;
        
        try {
        CommPort commPort = candidatePort.open(this.getClass().getName(), 2000);

            SerialPort serialPort = (SerialPort) commPort;
            serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

            OutputStream out = serialPort.getOutputStream();
            //send "?"
            out.write("?".getBytes());
            out.close();
            
            InputStream in = serialPort.getInputStream();
            long startedWaiting = System.currentTimeMillis();
            while(System.currentTimeMillis() - startedWaiting < 1000) {
                if (in.available()>=8) {
                    byte[] inputBytes = new byte[128];
                    int l = in.read(inputBytes);
                    if (l>=8) {
                        String response = new String(inputBytes,0,l);
                        System.out.print(response);
                        responseOk = response.startsWith("!xfd");
                    }
                }
            }
            in.close();
            serialPort.close();
            return responseOk;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update the Xfd Display with the given status per channel. Works by
     * left-shifting a 1 bit into the channels place for each of the red,
     * yellow, or green bytes.
     */
    void updateXfd(XfdStatus[] statusByChannel) throws PortInUseException, UnsupportedCommOperationException, IOException {
        int r = 0;
        int y = 0;
        int g = 0;
        int x = 0;
        for (int ch = 0; ch < 8; ch++) {
            XfdStatus status = statusByChannel[ch];
            
            if (status.isColor(XfdColor.RED)) {
                r += (1 << ch);
            } else if (status.isColor(XfdColor.GREEN)) {
                g += (1 << ch);
            } else if (status.isColor(XfdColor.YELLOW)) {
                y += (1 << ch);
            }
            
            if (status.isRunning()) {
                x += (1 << ch);
            }
        }
        if (portIdentifier == null) {
            portIdentifier = guessPort();
        }
        updateXfd(portIdentifier, r, y, g, x);
    }

    /**
     * Guess a port. Finds first serial usb port starting from portMin.
     */
    private CommPortIdentifier guessPort() {
        int portMin = propertyToInt("portMin", 8);
        int portMax = propertyToInt("portMax", 20);

        for (int portNr = portMin; portNr <= portMax; portNr++) {
            try {
                CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier("COM" + portNr);
                if (portIdentifier != null && portIdentifier.getPortType() == CommPortIdentifier.PORT_SERIAL
                        && !portIdentifier.isCurrentlyOwned()) {
                    System.out.println("guessing port " + portIdentifier.getName());
                    return portIdentifier;
                }
            } catch (NoSuchPortException nspe) {
                // silently ignore
                // System.out.println("Port COM" + portNr + " does not exist");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
        System.out.println("no suitable port found between " + portMin + " and " + portMax);
        System.exit(-1);
        return null;
    }

    /**
     * Read an Integer from a property with the given name. Use the default
     * value if property is not found or can not be parsed to Integer.
     */
    int propertyToInt(String name, int defaultValue) {
        String value = properties.getProperty(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                // ignore exception - reported below
            }
        }
        System.err.println("Could not parse value for property " + name + ", using default value " + defaultValue);
        return defaultValue;
    }

    /**
     * Update the XFD with given byte values for red, yellow, green and executing.
     */
    private void updateXfd(CommPortIdentifier port, int r, int y, int g, int x) throws PortInUseException, UnsupportedCommOperationException,
            IOException {
        if (portIdentifier.isCurrentlyOwned()) {
            System.out.println("Error: Port is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);

            if (commPort instanceof SerialPort) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

                OutputStream out = serialPort.getOutputStream();

                xfdDisplay(out, r, y, g, x);

                serialPort.close();
            } else {
                System.out.println("Error: Only serial ports are handled by this example.");
            }
        }
    }

    /**
     * Write the output bytes for r,y,g,x to the given output stream.
     */
    private void xfdDisplay(OutputStream out, int r, int y, int g, int x) throws IOException {
        String msg = "r" + r + "y" + y + "g" + g + "x" + x + ";";
        out.write(msg.getBytes());
        
        // debug to console
        //System.out.println("r:"+binary(r)+"  y:"+binary(y)+"  g:"+binary(g)+"  x:"+binary(x));
    }

    /**
     * Return x as 8-bit String of bits, for debugging. Inefficient.
     */
    private String binary(int x) {
        String binary = Integer.toBinaryString(x);
        while (binary.length()<8) {
            binary = "0"+binary;
        }
        return binary + " ("+x+")";
    }
}
