package org.talkingtv.control;

import java.io.IOException;

/**
 * Changes channels on a cable or satellite box. DirecTV is the first one; others
 * (another satellite box, a different TV service) can be added as new classes.
 * All methods block, so call them off the main thread.
 */
public interface ChannelTuner {
    void tune(int channel) throws IOException;

    void channelUp() throws IOException;

    void channelDown() throws IOException;

    /** A short sentence about what is on now, such as "ESPN, channel 206, SportsCenter". */
    String whatsOn() throws IOException;
}
