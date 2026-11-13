package io.github.drawerdev.kurakagi.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 * RFC 4330 compliant Simple Network Time Protocol (SNTP) Client.
 * Queries an NTP server (UDP 123) to compute local clock offset.
 * Fully compatible with Android 2.1 (API 7).
 */
public class SntpClient {

    public static final int NTP_PORT = 123;
    public static final int DEFAULT_TIMEOUT_MS = 5000;
    public static final String DEFAULT_HOST = "pool.ntp.org";

    private static final int NTP_PACKET_SIZE = 48;
    private static final int NTP_MODE_CLIENT = 3;
    private static final int NTP_MODE_SERVER = 4;
    private static final int NTP_MODE_BROADCAST = 5;
    private static final int NTP_VERSION = 3;

    private static final int ORIGINATE_TIME_OFFSET = 24;
    private static final int RECEIVE_TIME_OFFSET = 32;
    private static final int TRANSMIT_TIME_OFFSET = 40;

    // Number of seconds between Jan 1, 1900 and Jan 1, 1970
    // 70 years, 17 leap years: (70 * 365 + 17) * 86400 = 2,208,988,800
    public static final long OFFSET_1900_TO_1970 = 2208988800L;

    private long ntpTime;
    private long ntpTimeReference;
    private long roundTripTime;
    private long clockOffset;

    public SntpClient() {
    }

    /**
     * Sends an SNTP request to the specified host and updates time metrics.
     *
     * @param host NTP server hostname or IP address
     * @param timeoutMs Socket timeout in milliseconds
     * @return true if successful, false otherwise
     */
    public boolean requestTime(String host, int timeoutMs) {
        if (host == null || host.length() == 0) {
            return false;
        }

        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS);

            InetAddress address = InetAddress.getByName(host);
            byte[] buffer = new byte[NTP_PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, NTP_PORT);

            // Byte 0: LI = 0 (no warning), VN = 3 (version 3), Mode = 3 (client)
            buffer[0] = (byte) ((NTP_MODE_CLIENT) | (NTP_VERSION << 3));

            // Write client transmit timestamp (T1)
            long requestTime = System.currentTimeMillis();
            writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime);

            socket.send(packet);

            // Receive response
            packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            long responseTime = System.currentTimeMillis();

            SntpResult result = parseResponse(buffer, requestTime, responseTime);
            if (result == null) {
                return false;
            }

            this.clockOffset = result.clockOffset;
            this.roundTripTime = result.roundTripTime;
            this.ntpTime = result.ntpTime;
            this.ntpTimeReference = responseTime;

            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    /**
     * Static utility to request clock offset in milliseconds.
     *
     * @param host Hostname of NTP server
     * @param timeoutMs Timeout in milliseconds
     * @return Clock offset in milliseconds (NTP time - System time)
     * @throws IOException If network error or timeout occurs
     */
    public static long requestTimeOffset(String host, int timeoutMs) throws IOException {
        SntpClient client = new SntpClient();
        if (client.requestTime(host, timeoutMs)) {
            return client.getClockOffset();
        }
        throw new IOException("Failed to obtain SNTP time from " + host);
    }

    /**
     * Parses a received 48-byte NTP response buffer.
     */
    public static SntpResult parseResponse(byte[] buffer, long requestTime, long responseTime) {
        if (buffer == null || buffer.length < NTP_PACKET_SIZE) {
            return null;
        }

        int mode = buffer[0] & 0x07;
        int stratum = buffer[1] & 0xFF;

        if (mode != NTP_MODE_SERVER && mode != NTP_MODE_BROADCAST) {
            return null;
        }
        if (stratum == 0) {
            // Kiss-of-Death packet
            return null;
        }

        long originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET);
        long receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET);
        long transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET);

        if (transmitTime == 0) {
            return null;
        }

        // T1: request departure (client)
        long t1 = originateTime != 0 ? originateTime : requestTime;
        // T2: request arrival (server)
        long t2 = receiveTime;
        // T3: reply departure (server)
        long t3 = transmitTime;
        // T4: reply arrival (client)
        long t4 = responseTime;

        long roundTripTime = (t4 - t1) - (t3 - t2);
        long clockOffset = ((t2 - t1) + (t3 - t4)) / 2;
        long ntpTime = t4 + clockOffset;

        return new SntpResult(clockOffset, roundTripTime, ntpTime);
    }

    public long getNtpTime() {
        return ntpTime;
    }

    public long getNtpTimeReference() {
        return ntpTimeReference;
    }

    public long getRoundTripTime() {
        return roundTripTime;
    }

    public long getClockOffset() {
        return clockOffset;
    }

    /**
     * Reads a 32-bit unsigned word from the buffer at the given offset.
     */
    private static long read32(byte[] buffer, int offset) {
        long b0 = buffer[offset] & 0xFFL;
        long b1 = buffer[offset + 1] & 0xFFL;
        long b2 = buffer[offset + 2] & 0xFFL;
        long b3 = buffer[offset + 3] & 0xFFL;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    /**
     * Reads an NTP timestamp from the buffer at offset and converts to Unix ms.
     */
    public static long readTimeStamp(byte[] buffer, int offset) {
        long seconds = read32(buffer, offset);
        long fraction = read32(buffer, offset + 4);
        if (seconds == 0 && fraction == 0) {
            return 0;
        }
        return ((seconds - OFFSET_1900_TO_1970) * 1000L) + (((fraction * 1000L) + 0x80000000L) / 0x100000000L);
    }

    /**
     * Writes a Unix timestamp (ms) as an 8-byte NTP timestamp into buffer at offset.
     */
    public static void writeTimeStamp(byte[] buffer, int offset, long timeMs) {
        long seconds = (timeMs / 1000L) + OFFSET_1900_TO_1970;
        long fraction = ((timeMs % 1000L) * 0x100000000L) / 1000L;

        buffer[offset] = (byte) ((seconds >> 24) & 0xFF);
        buffer[offset + 1] = (byte) ((seconds >> 16) & 0xFF);
        buffer[offset + 2] = (byte) ((seconds >> 8) & 0xFF);
        buffer[offset + 3] = (byte) (seconds & 0xFF);

        buffer[offset + 4] = (byte) ((fraction >> 24) & 0xFF);
        buffer[offset + 5] = (byte) ((fraction >> 16) & 0xFF);
        buffer[offset + 6] = (byte) ((fraction >> 8) & 0xFF);
        buffer[offset + 7] = (byte) (fraction & 0xFF);
    }

    public static class SntpResult {
        public final long clockOffset;
        public final long roundTripTime;
        public final long ntpTime;

        public SntpResult(long clockOffset, long roundTripTime, long ntpTime) {
            this.clockOffset = clockOffset;
            this.roundTripTime = roundTripTime;
            this.ntpTime = ntpTime;
        }
    }
}
