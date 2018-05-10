package com.nilhcem.ledcontrol;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;

/**
 * Simplified port of Arduino's LedControl library
 */
public class LedControl implements AutoCloseable {

    //the opcodes for the MAX7221 and MAX7219
    private static final byte OP_NOOP = 0;
    private static final byte OP_DIGIT0 = 1;
    private static final byte OP_DIGIT1 = 2;
    private static final byte OP_DIGIT2 = 3;
    private static final byte OP_DIGIT3 = 4;
    private static final byte OP_DIGIT4 = 5;
    private static final byte OP_DIGIT5 = 6;
    private static final byte OP_DIGIT6 = 7;
    private static final byte OP_DIGIT7 = 8;
    private static final byte OP_DECODEMODE = 9;
    private static final byte OP_INTENSITY = 10;
    private static final byte OP_SCANLIMIT = 11;
    private static final byte OP_SHUTDOWN = 12;
    private static final byte OP_DISPLAYTEST = 15;

    /**
     * Segments to be switched on for characters and digits on 7-Segment Displays
     */
    private static final byte[] CHAR_TABLE = new byte[]{
            (byte) 0b01111110, (byte) 0b00110000, (byte) 0b01101101, (byte) 0b01111001, (byte) 0b00110011, (byte) 0b01011011, (byte) 0b01011111, (byte) 0b01110000,
            (byte) 0b01111111, (byte) 0b01111011, (byte) 0b01110111, (byte) 0b00011111, (byte) 0b00001101, (byte) 0b00111101, (byte) 0b01001111, (byte) 0b01000111,
            (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b10000000, (byte) 0b00000001, (byte) 0b10000000, (byte) 0b00000000,
            (byte) 0b01111110, (byte) 0b00110000, (byte) 0b01101101, (byte) 0b01111001, (byte) 0b00110011, (byte) 0b01011011, (byte) 0b01011111, (byte) 0b01110000,
            (byte) 0b01111111, (byte) 0b01111011, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b00000000, (byte) 0b01110111, (byte) 0b00011111, (byte) 0b00001101, (byte) 0b00111101, (byte) 0b01001111, (byte) 0b01000111, (byte) 0b00000000,
            (byte) 0b00110111, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00001110, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b01100111, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00001000,
            (byte) 0b00000000, (byte) 0b01110111, (byte) 0b00011111, (byte) 0b00001101, (byte) 0b00111101, (byte) 0b01001111, (byte) 0b01000111, (byte) 0b00000000,
            (byte) 0b00110111, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00001110, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b01100111, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000,
            (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000, (byte) 0b00000000
    };


    private SpiDevice spiDevice;

    /* The array for shifting the data to the devices */
    private byte[] spidata = new byte[2];

    /* We keep track of the led-status in this array */
    private byte[] status = new byte[8];

    public LedControl(String spiGpio) throws IOException {
        PeripheralManager manager = PeripheralManager.getInstance();
        spiDevice = manager.openSpiDevice(spiGpio);
        spiDevice.setMode(SpiDevice.MODE0);
        spiDevice.setFrequency(1000000); // 1MHz
        spiDevice.setBitsPerWord(8); // 8 BPW
        spiDevice.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST); // MSB first

        spiTransfer(OP_DECODEMODE, 0); // decoding： BCD
        setScanLimit(7); // scanlimit: 8 LEDs
        spiTransfer(OP_DISPLAYTEST, 0);

        shutdown(false);
        setIntensity(3);
        clearDisplay();
    }

    @Override
    public void close() throws IOException {
        try {
            spiDevice.close();
        } finally {
            spiDevice = null;
        }
    }

    /**
     * Set the shutdown (power saving) mode for the device
     *
     * @param status if true the device goes into power-down mode. Set to false for normal operation.
     */
    public void shutdown(boolean status) throws IOException {
        spiTransfer(OP_SHUTDOWN, status ? 0 : 1);
    }

    /**
     * Set the number of digits (or rows) to be displayed.
     * <p>
     * See datasheet for sideeffects of the scanlimit on the brightness of the display
     * </p>
     *
     * @param limit number of digits to be displayed (1..8)
     */
    public void setScanLimit(int limit) throws IOException {
        if (limit >= 0 || limit < 8) {
            spiTransfer(OP_SCANLIMIT, limit);
        }
    }

    /**
     * Set the brightness of the display
     *
     * @param intensity the brightness of the display. (0..15)
     */
    public void setIntensity(int intensity) throws IOException {
        if (intensity >= 0 || intensity < 16) {
            spiTransfer(OP_INTENSITY, intensity);
        }
    }

    /**
     * Switch all Leds on the display off
     */
    public void clearDisplay() throws IOException {
        for (int i = 0; i < 8; i++) {
            status[i] = 0;
            spiTransfer((byte) (i + 1), status[i]);
        }
    }

    /**
     * Set the status of a single Led
     *
     * @param row   the row of the Led (0..7)
     * @param col   the column of the Led (0..7)
     * @param state if true the led is switched on, if false it is switched off
     * @throws IOException
     */
    public void setLed(int row, int col, boolean state) throws IOException {
        byte val;

        if (row < 0 || row > 7 || col < 0 || col > 7) {
            return;
        }
        val = (byte) (0b10000000 >> col);
        if (state) {
            status[row] = (byte) (status[row] | val);
        } else {
            val = (byte) ~val;
            status[row] = (byte) (status[row] & val);
        }
        spiTransfer((byte) (row + 1), status[row]);
    }

    /**
     * Set all 8 Led's in a row to a new state
     *
     * @param row   row which is to be set (0..7)
     * @param value each bit set to 1 will light up the corresponding Led.
     */
    public void setRow(int row, byte value) throws IOException {
        if (row < 0 || row > 7) {
            return;
        }

        status[row] = value;
        spiTransfer((byte) (OP_DIGIT0 + row), status[row]);
    }

    /**
     * Set all 8 Led's in a column to a new state
     *
     * @param col   column which is to be set (0..7)
     * @param value each bit set to 1 will light up the corresponding Led.
     */
    public void setColumn(int col, byte value) throws IOException {
        byte val;

        if (col < 0 || col > 7) {
            return;
        }
        for (int row = 0; row < 8; row++) {
            val = (byte) (value >> (7 - row));
            setLed(row, col, (val & 0x01) == 0x01);
        }
    }

    /**
     * Display a hexadecimal digit on a 7-Segment Display
     *
     * @param digit the position of the digit on the display (0..7)
     * @param value the value to be displayed. (0x00..0x0F. 0x10 to clear digit)
     * @param dp    sets the decimal point.
     */
    public void setDigit(int digit, byte value, boolean dp) throws IOException {
        byte v;

        if (digit < 0 || digit > 7 || value > 16) {
            return;
        }
        v = CHAR_TABLE[value];
        if (dp) {
            v |= 0b10000000;
        }
        status[digit] = v;
        spiTransfer((byte) (digit + 1), v);
    }

    /**
     * Display a character on a 7-Segment display.
     * <pre>
     * There are only a few characters that make sense here :
     * '0','1','2','3','4','5','6','7','8','9','0',
     * 'A','b','c','d','E','F','H','L','P',
     * '.','-','_',' '
     * </pre>
     *
     * @param digit the position of the character on the display (0..7)
     * @param value the character to be displayed.
     * @param dp    sets the decimal point.
     */
    public void setChar(int digit, char value, boolean dp) throws IOException {
        byte index;
        byte v;

        if (digit < 0 || digit > 7) {
            return;
        }
        index = (byte) value;
        v = CHAR_TABLE[index];
        if (dp) {
            v |= 0b10000000;
        }
        status[digit] = v;
        spiTransfer((byte) (digit + 1), v);
    }

    /**
     * Draw the given bitmap to the LED matrix.
     *
     * @param bitmap Bitmap to draw
     * @throws IOException
     */
    public void draw(Bitmap bitmap) throws IOException {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true);
        for (int row = 0; row < 8; row++) {
            int value = 0;
            for (int col = 0; col < 8; col++) {
                value |= scaled.getPixel(col, row) == Color.WHITE ? (0x80 >> col) : 0;
            }
            setRow(row, (byte) value);
        }
    }

    /**
     * Send out a single command to the device
     */
    private void spiTransfer(byte opcode, int data) throws IOException {
        spidata[0] = opcode;
        spidata[1] = (byte) data;
        spiDevice.write(spidata, 2);
    }
}
