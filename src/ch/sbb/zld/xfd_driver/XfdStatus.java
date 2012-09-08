package ch.sbb.zld.xfd_driver;

/**
 * Status of a jenkins job or group of jobs. Consists of a color (red, yellow,
 * green) and a flag if the job is currently running.
 */
public final class XfdStatus {

    private XfdColor color;
    private boolean running;

    public XfdColor getColor() {
        return color;
    }

    public void setColor(XfdColor color) {
        this.color = color;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * Returns true if the color is equal to the given comparison color.
     */
    public boolean isColor(XfdColor comparison) {
        return comparison == getColor();
    }
    
    @Override
    public String toString() {
        return "color: "+color+", running: "+running;
    }
}
