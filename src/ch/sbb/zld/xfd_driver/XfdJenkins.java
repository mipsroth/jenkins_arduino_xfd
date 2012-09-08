package ch.sbb.zld.xfd_driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Methods for sending an array of colors to the XFD using USB serial.
 */
public class XfdJenkins {

    private Properties properties;

    XfdJenkins(Properties properties) {
        this.properties = properties;
    }

    /**
     * Read the URL specified in the given property. Returns a map with job name
     * as the key and an XfdColor as value.
     */
    Map<String, XfdStatus> getJobStatus(String urlPropertyName) throws IOException {
        String urlString = properties.getProperty(urlPropertyName);

        if (urlString == null || urlString.length() == 0) {
            throw new RuntimeException("could not read property " + urlPropertyName);
        }
        String json = "";
        try {
            json = readUrl(urlString);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return parseJson(json);
    }

    /**
     * Return the content of the given URL as a String.
     * @throws IOException 
     */
    private String readUrl(String urlString) throws IOException {
        URL u = new URL(urlString);
        InputStream is = u.openStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        StringBuffer content = new StringBuffer(12 * 1024);
        String line;
        while ((line = br.readLine()) != null) {
            content.append(line);
            content.append("\n");
        }
        br.close();
        return content.toString();
    }

    /**
     * Parse the Jenkins JSON jobs and their color from the given Json String.
     */
    private Map<String, XfdStatus> parseJson(String json) {
        Map<String, XfdStatus> statusByJobName = new HashMap<String, XfdStatus>(50);

        int jobsFrom = json.indexOf("jobs\":[{") + 8;
        int jobsTo = json.indexOf("}]", jobsFrom);
        if (jobsFrom < 0 || jobsTo<= 0) {
            // not found - return empty map
            return statusByJobName;
        }
        String jobsSubstring = json.substring(jobsFrom, jobsTo);
        StringTokenizer tokenizer = new StringTokenizer(jobsSubstring, "}");
        while (tokenizer.hasMoreTokens()) {
            String oneJob = tokenizer.nextToken();

            int nameFrom = oneJob.indexOf("name\":\"") + 7;
            int nameTo = oneJob.indexOf("\"", nameFrom);
            String name = oneJob.substring(nameFrom, nameTo);

            int colorFrom = oneJob.indexOf("color\":\"") + 8;
            int colorTo = oneJob.indexOf("\"", colorFrom);
            String color = oneJob.substring(colorFrom, colorTo);

            XfdStatus status = new XfdStatus();
            
            if (color.startsWith("blue")) {
                // blue is green
                status.setColor(XfdColor.GREEN);
            } else if (color.startsWith("red")) {
                status.setColor(XfdColor.RED);
            } else if (color.startsWith("yellow")) {
                status.setColor(XfdColor.YELLOW);
            } else if (color.startsWith("abort")) {
                // treat "aborted" as yellow
                status.setColor(XfdColor.YELLOW);
            } else {
                // everything else, e.g. "disabled", is blank
                status.setColor(XfdColor.BLANK);
            }
            
            if (color.contains("anim")) {
                status.setRunning(true);
            }
            
            statusByJobName.put(name, status);
        }
        return statusByJobName;
    }

}
