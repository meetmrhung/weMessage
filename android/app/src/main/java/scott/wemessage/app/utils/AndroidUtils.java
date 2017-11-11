package scott.wemessage.app.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.webkit.MimeTypeMap;

import com.stfalcon.chatkit.utils.DateFormatter;

import java.util.Calendar;
import java.util.Date;

import scott.wemessage.R;
import scott.wemessage.commons.types.MimeType;
import scott.wemessage.commons.utils.DateUtils;

public class AndroidUtils {

    public static boolean hasMemoryForOperation(long memoryNeeded){
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        long maxHeapSize = runtime.maxMemory() / 1048576L;
        long availableHeapSize = maxHeapSize - usedMemory;

        if ((memoryNeeded / 1048576L) > (availableHeapSize - 10)) {
            return false;
        }
        return true;
    }

    public static String getDeviceName(){
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        String deviceName;

        if (adapter != null) {
            deviceName = adapter.getName();
        } else {
            deviceName = getInternalDeviceName();
        }
        return deviceName;
    }

    public static MimeType getMimeTypeFromPath(String path){
        return MimeType.getTypeFromString(MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path).toLowerCase()));
    }

    public static String getMimeTypeStringFromPath(String path){
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path).toLowerCase());
    }

    public static String processDate(Context context, Date date, boolean shortDate){
        if (DateFormatter.isToday(date)){
            return DateFormatter.format(date, "h:mm a");
        }else if (DateFormatter.isYesterday(date)){
            return context.getString(R.string.word_yesterday);
        }else if (DateUtils.isSameWeek(date)){
            return getDayFromDate(context, date);
        } else {
            if (DateFormatter.isCurrentYear(date)){
                if (shortDate) {
                    return DateFormatter.format(date, "M/d/yy");
                }else {
                    return DateFormatter.format(date, "MMMM d");
                }
            }else {
                if (shortDate){
                    return DateFormatter.format(date, "M/d/yy");
                }else {
                    return DateFormatter.format(date, "MMMM d, yyyy");
                }
            }
        }
    }

    public static String getDayFromDate(Context context, Date date){
        Calendar c = Calendar.getInstance();
        c.setTime(date);

        int day = c.get(Calendar.DAY_OF_WEEK);

        switch (day){
            case Calendar.SUNDAY:
                return context.getString(R.string.word_sunday);
            case Calendar.MONDAY:
                return context.getString(R.string.word_monday);
            case Calendar.TUESDAY:
                return context.getString(R.string.word_tuesday);
            case Calendar.WEDNESDAY:
                return context.getString(R.string.word_wednesday);
            case Calendar.THURSDAY:
                return context.getString(R.string.word_thursday);
            case Calendar.FRIDAY:
                return context.getString(R.string.word_friday);
            case Calendar.SATURDAY:
                return context.getString(R.string.word_saturday);
            default:
                return DateFormatter.format(date, "MMMM d");
        }
    }

    private static String getInternalDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }


    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);

        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
}