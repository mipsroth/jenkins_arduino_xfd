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
            Map<String, XfdStatus> zldJobs = jenkins.getJobStatus("urlZld");
            Map<String, XfdStatus> ciJobs = jenkins.getJobStatus("urlCi");

            XfdStatus[] statusByChannel = determineXfdState(zldJobs, ciJobs);
            communicator.updateXfd(statusByChannel);

            Thread.sleep(1000);
        }
    }

    /**
     * Determine the colors for each of the 8 channels (array index 0-7).
     */
    private XfdStatus[] determineXfdState(Map<String, XfdStatus> zldJobs, Map<String, XfdStatus> ciJobs) {
        XfdStatus[] statusByChannel = new XfdStatus[8];
        int maxAggregationChannel = communicator.propertyToInt("aggregateTo", 8);
        List<XfdStatus> statusToAggregate = new ArrayList<XfdStatus>(8);
        for (int channel = 1; channel < 8; channel++) {
            statusByChannel[channel] = determineXfdState(channel, zldJobs, ciJobs);
            if (channel <= maxAggregationChannel) {
                statusToAggregate.add(statusByChannel[channel]);
            }
        }
        statusByChannel[0] = aggregate(statusToAggregate);
        return statusByChannel;
    }

    /**
     * Determine the given channel number's status from the map of jobs and their
     * respective colors. Matches jobs agains the regex from properties for the
     * channel. Example: - jenkins.channel.1=zld -
     * regex.channel.1=ZLD-Compile.*|ZLD-Check.*
     */
    private XfdStatus determineXfdState(int channel, Map<String, XfdStatus> zldJobs, Map<String, XfdStatus> ciJobs) {
        String channelKey = "channel." + channel;
        String ci = properties.getProperty("jenkins." + channelKey);
        String regex = properties.getProperty("regex." + channelKey);

        List<XfdStatus> matchingStatus = new ArrayList<XfdStatus>(50);
        if (ci.startsWith("ci")) {
            addMatching(regex, ciJobs, matchingStatus);
        } else {
            addMatching(regex, zldJobs, matchingStatus);
        }
        return aggregate(matchingStatus);
    }

    private void addMatching(String regex, Map<String, XfdStatus> jobs, List<XfdStatus> matchingColors) {
        for (String job : jobs.keySet()) {
            if (job.matches(regex)) {
                matchingColors.add(jobs.get(job));
            }
        }
    }

    /**
     * Determine the aggregate XfdStatus for the List of XfdStatus.
     * Colors:
     * all blank -> blank
     * any red -> red
     * all green -> green
     * otherwise -> yellow
     * Running:
     * any running -> isRunning
     */
    private XfdStatus aggregate(List<XfdStatus> statusses) {
        boolean allGreen = true;
        boolean anyRed = false;
        boolean allBlank = true;
        boolean anyRunning = false;
        for (XfdStatus status : statusses) {
            
            if (status.isColor(XfdColor.BLANK)) {
                // no influence
            } else {
                allBlank = false;
                if (!status.isColor(XfdColor.GREEN)) {
                    allGreen = false;
                }
                if (status.isColor(XfdColor.RED)) {
                    anyRed = true;
                }
            }
            
            if (status.isRunning()) {
                anyRunning = true;
            }
        }
        
        XfdStatus status = new XfdStatus();
        if (allBlank) {
            status.setColor(XfdColor.BLANK);
        } else if (anyRed) {
            status.setColor(XfdColor.RED);
        } else if (allGreen) {
            status.setColor(XfdColor.GREEN);
        } else {
            status.setColor(XfdColor.YELLOW);
        }
        status.setRunning(anyRunning);
        return status;
    }

}