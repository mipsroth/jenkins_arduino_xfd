package ch.sbb.zld.xfd_driver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Main XFD driver software, periodically updating the XFD to the latest Jenkins
 * state.
 */
public final class XfdDriver {

    private Properties properties = new Properties();
    private XfdJenkins jenkins = new XfdJenkins(properties);
    private XfdCommunicator communicator = new XfdCommunicator(properties);

    /**
     * main(). Reads properties file then loop eternally and updates the XFD.
     */
    public static void main(String[] args) {
        XfdDriver me = new XfdDriver();
        try {
            me.init();
            me.loop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads xfd.properties.
     */
    private void init() throws FileNotFoundException, IOException {
        properties.load(new FileInputStream("xfd.properties"));
    }

    /**
     * Main loop. Get jenkins status and update xfd. Runs forever (or until an
     * exception occurs).
     */
    private void loop() throws Exception {
        while (true) {
            Map<String, XfdColor> zldJobs = jenkins.getJobStatus("urlZld");
            Map<String, XfdColor> ciJobs = jenkins.getJobStatus("urlCi");

            XfdColor[] colorByChannel = determineXfdState(zldJobs, ciJobs);
            communicator.updateXfd(colorByChannel);

            Thread.sleep(1000);
        }
    }

    /**
     * Determine the colors for each of the 8 channels (array index 0-7).
     */
    private XfdColor[] determineXfdState(Map<String, XfdColor> zldJobs, Map<String, XfdColor> ciJobs) {
        XfdColor[] colorByChannel = new XfdColor[8];
        int maxAggregationChannel = communicator.propertyToInt("aggregateTo", 8);
        List<XfdColor> colorToAggregate = new ArrayList<XfdColor>(8);
        for (int channel = 1; channel < 8; channel++) {
            colorByChannel[channel] = determineXfdState(channel, zldJobs, ciJobs);
            if (channel <= maxAggregationChannel) {
                colorToAggregate.add(colorByChannel[channel]);
            }
        }
        colorByChannel[0] = aggregate(colorToAggregate);
        return colorByChannel;
    }

    /**
     * Determine the given channel number's color from the map of jobs and their
     * respective colors. Matches jobs agains the regex from properties for the
     * channel. Example: - jenkins.channel.1=zld -
     * regex.channel.1=ZLD-Compile.*|ZLD-Check.*
     */
    private XfdColor determineXfdState(int channel, Map<String, XfdColor> zldJobs, Map<String, XfdColor> ciJobs) {
        String channelKey = "channel." + channel;
        String ci = properties.getProperty("jenkins." + channelKey);
        String regex = properties.getProperty("regex." + channelKey);

        List<XfdColor> matchingColors = new ArrayList<XfdColor>(50);
        if (ci.startsWith("ci")) {
            addMatching(regex, ciJobs, matchingColors);
        } else {
            addMatching(regex, zldJobs, matchingColors);
        }
        return aggregate(matchingColors);
    }

    private void addMatching(String regex, Map<String, XfdColor> jobs, List<XfdColor> matchingColors) {
        for (String job : jobs.keySet()) {
            if (job.matches(regex)) {
                matchingColors.add(jobs.get(job));
            }
        }
    }

    /**
     * Determine the aggregate color for the List of XfdColors.
     */
    private XfdColor aggregate(List<XfdColor> colors) {
        boolean allGreen = true;
        boolean anyRed = false;
        boolean allBlank = true;
        for (XfdColor color : colors) {
            if (XfdColor.BLANK == color) {
                // no influence
            } else {
                allBlank = false;
                if (XfdColor.GREEN != color) {
                    allGreen = false;
                }
                if (XfdColor.RED == color) {
                    anyRed = true;
                }
            }

        }
        if (allBlank) {
            return XfdColor.BLANK;
        } else if (anyRed) {
            return XfdColor.RED;
        } else if (allGreen) {
            return XfdColor.GREEN;
        } else {
            return XfdColor.YELLOW;
        }
    }

}