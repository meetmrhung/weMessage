/*
 *  weMessage - iMessage for Android
 *  Copyright (C) 2018 Roman Scott
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package scott.wemessage.app.messages.firebase;

import android.content.Context;
import android.os.PowerManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import scott.wemessage.app.weMessage;

public class FirebaseNotificationService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if(!powerManager.isInteractive()) {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "WeMessageNotificationWakeLock");
            wakeLock.acquire(5 * 1000);
        }

        weMessage.get().getNotificationManager().showFirebaseNotification(this, remoteMessage);
    }
}