package com.nilhcem.blefun.common;

import java.nio.charset.Charset;
import java.util.UUID;

public class AwesomenessProfile {

    // UUID for the UART BTLE client characteristic which is necessary for notifications.
    public static UUID DESCRIPTOR_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static UUID DESCRIPTOR_USER_DESC = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");

    public static UUID SERVICE_UUID = UUID.fromString("795090c7-420d-4048-a24e-18e60180e23c");
    public static UUID CHARACTERISTIC_COUNTER_UUID = UUID.fromString("31517c58-66bf-470c-b662-e352a6c80cba");
    public static UUID CHARACTERISTIC_INTERACTOR_UUID = UUID.fromString("0b89d2d4-0ea6-4141-86bb-0c5fb91ab14a");

    public static byte[] getUserDescription(UUID characteristicUUID) {
        String desc;

        if (CHARACTERISTIC_COUNTER_UUID.equals(characteristicUUID)) {
            desc = "Indicates the number of time you have been awesome so far";
        } else if (CHARACTERISTIC_INTERACTOR_UUID.equals(characteristicUUID)) {
            desc = "Write any value here to move the cat’s paw and increment the awesomeness counter";
        } else {
            desc = "";
        }

        return desc.getBytes(Charset.forName("UTF-8"));
    }
}
