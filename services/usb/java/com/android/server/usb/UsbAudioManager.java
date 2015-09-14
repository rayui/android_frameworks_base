/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions an
 * limitations under the License.
 */

package com.android.server.usb;

import android.alsa.AlsaCardsParser;
import android.alsa.AlsaDevicesParser;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.media.AudioManager;
import android.os.UserHandle;
import android.util.Slog;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * UsbAudioManager manages USB audio devices.
 */
public class UsbAudioManager {
    private static final String TAG = UsbAudioManager.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final Context mContext;

    private final class AudioDevice {
        public int mCard;
        public int mDevice;
        public boolean mHasPlayback;
        public boolean mHasCapture;
        public boolean mHasMIDI;

        public AudioDevice(int card, int device,
                boolean hasPlayback, boolean hasCapture, boolean hasMidi) {
            mCard = card;
            mDevice = device;
            mHasPlayback = hasPlayback;
            mHasCapture = hasCapture;
            mHasMIDI = hasMidi;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("AudioDevice: [card: " + mCard);
            sb.append(", device: " + mDevice);
            sb.append(", hasPlayback: " + mHasPlayback);
            sb.append(", hasCapture: " + mHasCapture);
            sb.append(", hasMidi: " + mHasMIDI);
            sb.append("]");
            return sb.toString();
        }
    }

    private final HashMap<UsbDevice,AudioDevice> mAudioDevices
            = new HashMap<UsbDevice,AudioDevice>();

    /* package */ UsbAudioManager(Context context) {
        mContext = context;
    }

    // Broadcasts the arrival/departure of a USB audio interface
    // audioDevice - the AudioDevice that was added or removed
    // enabled - if true, we're connecting a device (it's arrived), else disconnecting
    private void sendDeviceNotification(AudioDevice audioDevice, boolean enabled) {
        // send a sticky broadcast containing current USB state
        Intent intent = new Intent(AudioManager.ACTION_USB_AUDIO_DEVICE_PLUG);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        intent.putExtra("state", enabled ? 1 : 0);
        intent.putExtra("card", audioDevice.mCard);
        intent.putExtra("device", audioDevice.mDevice);
        intent.putExtra("hasPlayback", audioDevice.mHasPlayback);
        intent.putExtra("hasCapture", audioDevice.mHasCapture);
        intent.putExtra("hasMIDI", audioDevice.mHasMIDI);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private boolean waitForAlsaUSBCards() {
        final int kNumRetries = 10;
        final int kSleepTime = 500; // ms
        String cardsFilePath = "/proc/asound/cards";
        File cardsFile = new File(cardsFilePath);
        boolean exists = false;

        for (int retry = 0; !exists && retry < kNumRetries; retry++) {
            try {
                FileReader reader = new FileReader(cardsFile);
                BufferedReader bufferedReader = new BufferedReader(reader);
                String line = "";
                while ((line = bufferedReader.readLine()) != null) {
                    // AML only record the USB audio card
                    Slog.d(TAG, "waitForAlsaUSBCards line:" + line);
                    if (line.indexOf("USB-Audio") >= 0) {
                        reader.close();
                        exists = true;
                        Slog.d(TAG, "waitForAlsaUSBCards USB audio device is exist");
                        return exists;
                    }
                }
                reader.close();
                Slog.w(TAG, "waitForAlsaUSBCards : retry: " + retry);
                Thread.sleep(kSleepTime);
            }catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (IllegalThreadStateException ex) {
                Slog.d(TAG, "usb: IllegalThreadStateException while waiting for ALSA USB file.");
            } catch (java.lang.InterruptedException ex) {
                Slog.d(TAG, "usb: InterruptedException while waiting for ALSA USB file.");
            }
        }
        return exists;
    }

    private boolean waitForAlsaFile(int card, int device, boolean capture) {
        // These values were empirically determined.
        final int kNumRetries = 5;
        final int kSleepTime = 500; // ms
        String alsaDevPath = "/dev/snd/pcmC" + card + "D" + device + (capture ? "c" : "p");
        File alsaDevFile = new File(alsaDevPath);
        boolean exists = false;
        for (int retry = 0; !exists && retry < kNumRetries; retry++) {
            exists = alsaDevFile.exists();
            if (!exists) {
                try {
                    Thread.sleep(kSleepTime);
                } catch (IllegalThreadStateException ex) {
                    Slog.d(TAG, "usb: IllegalThreadStateException while waiting for ALSA file.");
                } catch (java.lang.InterruptedException ex) {
                    Slog.d(TAG, "usb: InterruptedException while waiting for ALSA file.");
                }
            }
        }

        return exists;
    }

    /* package */ void deviceAdded(UsbDevice usbDevice) {
        // Is there an audio interface in there?
        boolean isAudioDevice = false;

        // FIXME - handle multiple configurations?
        int interfaceCount = usbDevice.getInterfaceCount();
        for (int ntrfaceIndex = 0; !isAudioDevice && ntrfaceIndex < interfaceCount;
                ntrfaceIndex++) {
            UsbInterface ntrface = usbDevice.getInterface(ntrfaceIndex);
            if (ntrface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
                isAudioDevice = true;
            }
        }
        if (!isAudioDevice) {
            return;
        }

        //TODO(pmclean) The "Parser" objects inspect files in "/proc/asound" which we presume is
        // present, unlike the waitForAlsaFile() which waits on a file in /dev/snd. It is not
        // clear why this works, or that it can be relied on going forward.  Needs further
        // research.
        AlsaCardsParser cardsParser = new AlsaCardsParser();
        if (!waitForAlsaUSBCards()) {
            Slog.w(TAG, "Timeout: wait more than 5s for USB audio device ready." );
            return;
        }
        cardsParser.scan();
        // cardsParser.Log();

        // But we need to parse the device to determine its capabilities.
        AlsaDevicesParser devicesParser = new AlsaDevicesParser();
        devicesParser.scan();
        // devicesParser.Log();

        // The protocol for now will be to select the last-connected (highest-numbered)
        // Alsa Card.
        // AML use the protocal to select the saved card but not the last-connected one; because
        // the last one may be other kind of card

        int card = cardsParser.getCardIndex(cardsParser.getNumCardRecords() - 1);
        //int card = cardsParser.getNumCardRecords() - 1;
        int device = 0;

        boolean hasPlayback = devicesParser.hasPlaybackDevices(card);
        boolean hasCapture = devicesParser.hasCaptureDevices(card);
        boolean hasMidi = devicesParser.hasMIDIDevices(card);

        // Playback device file needed/present?
        if (hasPlayback &&
            !waitForAlsaFile(card, device, false)) {
            return;
        }

        // Capture device file needed/present?
        if (hasCapture &&
            !waitForAlsaFile(card, device, true)) {
            return;
        }

        if (DEBUG) {
            Slog.d(TAG,
                    "usb: hasPlayback:" + hasPlayback + " hasCapture:" + hasCapture);
        }

        AudioDevice audioDevice = new AudioDevice(card, device, hasPlayback, hasCapture, hasMidi);
        mAudioDevices.put(usbDevice, audioDevice);
        sendDeviceNotification(audioDevice, true);
    }

    /* package */ void deviceRemoved(UsbDevice device) {
       if (DEBUG) {
          Slog.d(TAG, "deviceRemoved(): " + device);
        }

        AudioDevice audioDevice = mAudioDevices.remove(device);
        if (audioDevice != null) {
            sendDeviceNotification(audioDevice, false);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw) {
        pw.println("  USB AudioDevices:");
        for (UsbDevice device : mAudioDevices.keySet()) {
            pw.println("    " + device.getDeviceName() + ": " + mAudioDevices.get(device));
        }
    }
}
