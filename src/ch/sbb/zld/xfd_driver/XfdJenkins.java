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
    Map<String, XfdColor> getJobStatus(String urlPropertyName) throws IOException {
        String urlString = properties.getProperty(urlPropertyName);

        if (urlString == null || urlString.length() == 0) {
            throw new RuntimeException("could not read property " + urlPropertyName);
        }
        String json = readUrl(urlString);
        return parseJson(json);
    }

    /**
     * Return the content of the given URL as a String.
     */
    private String readUrl(String urlString) {
        try {
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
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return "";
        }
    }

    /**
     * Parse the Jenkins JSON jobs and their color from the given Json String.
     */
    private Map<String, XfdColor> parseJson(String json) {
        Map<String, XfdColor> colorByJobName = new HashMap<String, XfdColor>(50);

        int jobsFrom = json.indexOf("jobs\":[{") + 8;
        int jobsTo = json.indexOf("}]", jobsFrom);
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

            if (color.startsWith("blue")) {
                // blue is green
                colorByJobName.put(name, XfdColor.GREEN);
            } else if (color.startsWith("red")) {
                colorByJobName.put(name, XfdColor.RED);
            } else if (color.startsWith("yellow")) {
                colorByJobName.put(name, XfdColor.YELLOW);
            } else if (color.startsWith("abort")) {
                // treat "aborted" as yellow
                colorByJobName.put(name, XfdColor.YELLOW);
            } else {
                // everything else, e.g. "disabled", is blank
                colorByJobName.put(name, XfdColor.BLANK);
            }
        }
        return colorByJobName;
    }

}
