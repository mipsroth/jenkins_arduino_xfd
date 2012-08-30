package ch.sbb.zld.xfd_driver;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
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
     * Find a port of type SERIAL.
     */
    private void findPort() {
        @SuppressWarnings("unchecked")
        Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();
        while (portEnum.hasMoreElements()) {
            CommPortIdentifier candidatePort = portEnum.nextElement();
            if (candidatePort.getPortType() == CommPortIdentifier.PORT_SERIAL) {
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
     * Update the Xfd Display with the given colors per channel. Works by
     * left-shifting a 1 bit into the channels place for each of the red,
     * yellow, or green bytes.
     */
    void updateXfd(XfdColor[] colorByChannel) throws PortInUseException, UnsupportedCommOperationException, IOException {
        int r = 0;
        int y = 0;
        int g = 0;
        for (int ch = 0; ch < 8; ch++) {
            XfdColor color = colorByChannel[ch];
            if (XfdColor.RED == color) {
                r += (1 << ch);
            } else if (XfdColor.GREEN == color) {
                g += (1 << ch);
            } else if (XfdColor.YELLOW == color) {
                y += (1 << ch);
            }
        }
        if (portIdentifier == null) {
            portIdentifier = guessPort();
        }
        updateXfd(portIdentifier, r, y, g);
    }

    /**
     * Guess a port.
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
     * Update the XFD with given byte values for red, yellow and green.
     */
    private void updateXfd(CommPortIdentifier port, int r, int y, int g) throws PortInUseException, UnsupportedCommOperationException,
            IOException {
        if (portIdentifier.isCurrentlyOwned()) {
            System.out.println("Error: Port is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);

            if (commPort instanceof SerialPort) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

                OutputStream out = serialPort.getOutputStream();

                xfdDisplay(out, r, y, g);

                serialPort.close();
            } else {
                System.out.println("Error: Only serial ports are handled by this example.");
            }
        }
    }

    /**
     * Write the output bytes for r,y,g to the given output stream.
     */
    private void xfdDisplay(OutputStream out, int r, int y, int g) throws IOException {
        String msg = "r" + r + "y" + y + "g" + g + ";";
        out.write(msg.getBytes());
    }
}
