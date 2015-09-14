package com.android.server.zygotesecondary;

import android.os.Build;
import android.os.SystemProperties;

import com.android.server.pm.PackageManagerService;
import com.android.internal.util.ArrayUtils;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;

public final class ZygoteSecondary {

    public static final String TAG = "ZygoteSecondary";

    public static final int UNSUPPORT_ZYGOTE_SECONDARY = 0;

    public static final int START_ZYGOTE_SECONDARY_SUCESSFUL = 1;

    public static final int START_ZYGOTE_SECONDARY_FAILED = 2;

    public static final int STOP_ZYGOTE_SECONDARY_SUCESSFUL = 3;

    public static final int STOP_ZYGOTE_SECONDARY_FAILED = 4;

    public static final int DONOT_NEED_START_ZYGOTE_SECONDARY = 5;

    public static final int DONOT_NEED_STOP_ZYGOTE_SECONDARY = 6;

    public static final int DONOT_CARE_ZYGOTE_SECONDARY = 7;

    public static final int ERROR_ZYGOTE_SECONDARY = 8;

    // ro.dynamic.zygote_secondary=enable is enable dynamic
    // start/stop zygote_secondary feature
    public static final String RO_DYNAMIC_ZYGOTE_SECONDARY =
        "ro.dynamic.zygote_secondary";

    public static final String SYS_ZYGOTE_SECONDARY =
        "sys.zygote_secondary";

    public static final String PERSISIT_SYS_ZYGOTE_SECONDARY =
        "persist.sys.zygote_secondary";

    private static final boolean DEBUG = true;

    private static final int BIT32 = 32;

    private static final int BIT64 = 64;

    private static ArrayList<String> cpu32BitAbiList =
        new ArrayList<String>();

    private static ArrayList<String> cpu64BitAbiList =
        new ArrayList<String>();

    // apps have secondary native libary
    private static ArrayList<String> packageNameList =
        new ArrayList<String>();

    // false: zygote_secondary service has stopped
    // true: zygote_secondary service has started
    private static boolean mIsZygoteSecondaryStart = false;


    ZygoteSecondary() {}

    static {
        // Just beginning to get native status and updata status
        if (supportZygoteSecondaryStart() != null) {
            mIsZygoteSecondaryStartStatusUpdate(false);
            if (!ArrayUtils.isEmpty(Build.SUPPORTED_32_BIT_ABIS)) {
                Collections.addAll(cpu32BitAbiList, Build.SUPPORTED_32_BIT_ABIS);
            }

            if (!ArrayUtils.isEmpty(Build.SUPPORTED_64_BIT_ABIS)) {
                Collections.addAll(cpu64BitAbiList, Build.SUPPORTED_64_BIT_ABIS);
            }
        }
    }

    private static void mIsZygoteSecondaryStartStatusUpdate(
        boolean status) {
        if (status == true) {
            mIsZygoteSecondaryStart = true;
            Log.i(TAG, "zygote_secondary started");
            return;
        }

        if (SystemProperties.get(SYS_ZYGOTE_SECONDARY, "stop")
            .equals("start")) {
            mIsZygoteSecondaryStart = true;
            Log.i(TAG, "zygote_secondary started");
        } else {
            mIsZygoteSecondaryStart = false;
            Log.i(TAG, "zygote_secondary stopped");
        }
    }

    private static boolean isBootCompleted() {
        boolean isBootCompleted = false;
        String value = SystemProperties.get("sys.boot_completed", "0");
        if (value.equals("1")) {
            isBootCompleted = true;
        }
        return isBootCompleted;
    }

    private static boolean isZygoteSecondaryStart() {
        return mIsZygoteSecondaryStart;
    }

    private static boolean isZygoteSecondaryStop() {
        return !mIsZygoteSecondaryStart;
    }

    private static boolean ensureZygoteSecondaryStart() {
        if (isZygoteSecondaryStart()) {
            return true;
        }

        SystemProperties.set(SYS_ZYGOTE_SECONDARY, "start");
        // backup status for next boot
        SystemProperties.set(PERSISIT_SYS_ZYGOTE_SECONDARY, "start");
        String value = SystemProperties.get(SYS_ZYGOTE_SECONDARY, "");
        if (value.equals("start")) {
            mIsZygoteSecondaryStart = true;
        }
        return mIsZygoteSecondaryStart;
    }

    private static boolean ensureZygoteSecondaryStop() {
        if (isZygoteSecondaryStop()) {
            return true;
        }

        SystemProperties.set(SYS_ZYGOTE_SECONDARY, "stop");
        // backup status for next boot
        SystemProperties.set(PERSISIT_SYS_ZYGOTE_SECONDARY, "stop");
        String value = SystemProperties.get(SYS_ZYGOTE_SECONDARY, "");
        if (value.equals("stop")) {
            mIsZygoteSecondaryStart = false;
        }
        return !mIsZygoteSecondaryStart;
    }

    private static Integer supportZygoteSecondaryStart() {
        int zygote = 0;
        int zygote_secondary = 0;
        String value = SystemProperties.get("ro.zygote", "zygote32");
        if (value.equals("zygote64_32")) {
            zygote = BIT64;
            zygote_secondary = BIT32;
        } else if (value.equals("zygote32_64")) {
            zygote = BIT32;
            zygote_secondary = BIT64;
        } else {
            // unsupport zygote_secondary for zygote
            // only in 64bits or 32bits system
            return null;
        }

        return Integer.valueOf(zygote_secondary);
    }

    public static int getSecondaryNativeLibAppPackageNameNum() {
        return packageNameList.size();
    }

    private static void setSecondaryNativeLibAppPackageNameList(
        ArrayList<ApplicationInfo> applist) {
        if (applist != null && applist.size() > 0) {
            for (ApplicationInfo app : applist) {
                if (app != null) {
                    packageNameList.add(app.packageName);
                }
            }
        }
    }

    private static void updateSecondaryNativeLibAppPackageNameList(
        String packageName, boolean isAdd) {
        if (packageName == null) {
            return;
        }

        if (isAdd) {
            if (!packageNameList.contains(packageName)) {
                packageNameList.add(packageName);
            }
        } else {
            packageNameList.remove(packageName);
        }

        Log.i(TAG,
            "system remain " + getSecondaryNativeLibAppPackageNameNum() +
            " apps have secondary native lib");
        if (DEBUG) {
            int i = 0;
            for (String name : packageNameList) {
                Log.i(TAG, (++i) + ". "+
                    "\"" + name + "\"");
            }
        }
    }


    /**
     * Whether to need to start zygote_secondary after
     * install app successful.
     *
     * @param installStatus App to be installed status
     * @param ApplicationInfo App to be installed
     *
     * @return START_ZYGOTE_SECONDARY_SUCESSFUL
     *  if start zygote_secondary successful
     * @return START_ZYGOTE_SECONDARY_FAILED
     *  if start zygote_secondary failed
     * @return DONOT_CARE_ZYGOTE_SECONDARY
     *  if install failed or app hasn't secondary native library
     * @return ERROR_ZYGOTE_SECONDARY
     *  if some errors occurred
     * @return UNSUPPORT_ZYGOTE_SECONDARY
     *  if unsupport zygote_secondary
     */
    private static int installAppZygoteSecondaryStartOrNot(
        int installStatus, ApplicationInfo app) {
        Integer object = null;
        int zygote_secondary = 0;
        if ((object = supportZygoteSecondaryStart()) != null) {
            zygote_secondary = object.intValue();
            if (zygote_secondary != BIT32 &&
                zygote_secondary != BIT64) {
                Log.e(TAG,
                    "variable zygote_secondary("+zygote_secondary+") != 32/64");
                return ERROR_ZYGOTE_SECONDARY;
            }
        } else {
            return UNSUPPORT_ZYGOTE_SECONDARY;
        }

        Log.i(TAG, "install app: " + app.packageName);
        if (installStatus != PackageManager.INSTALL_SUCCEEDED) {
            Log.e(TAG,
                "don't care zygote_secondary(Install failed)");
            return DONOT_CARE_ZYGOTE_SECONDARY;
        }

        ArrayList<String> cpuAbiList =
            new ArrayList<String>();
        String [] appCpuAbi = new String [] {
            app.primaryCpuAbi, app.secondaryCpuAbi };

        if (zygote_secondary == BIT32) {
            cpuAbiList.addAll(cpu32BitAbiList);
        } else if (zygote_secondary == BIT64) {
            cpuAbiList.addAll(cpu64BitAbiList);
        }

        for (String abi : appCpuAbi) {
            if (abi != null && cpuAbiList.contains(abi)) {
                updateSecondaryNativeLibAppPackageNameList(
                    app.packageName, true);
                if (isZygoteSecondaryStart()) {
                    Log.i(TAG, "zygote_secondary has started");
                    return START_ZYGOTE_SECONDARY_SUCESSFUL;
                }

                if (ensureZygoteSecondaryStart()) {
                    Log.i(TAG, "start zygote_secondary successful");
                    return START_ZYGOTE_SECONDARY_SUCESSFUL;
                } else {
                    Log.e(TAG, "start zygote_secondary failed");
                    return START_ZYGOTE_SECONDARY_FAILED;
                }
            }
        }

        Log.i(TAG,
            "primaryCpuAbi:" + app.primaryCpuAbi +
            ", secondaryCpuAbi:" + app.secondaryCpuAbi);
        return DONOT_CARE_ZYGOTE_SECONDARY;
    }


    /**
     * Whether to need to stop zygote_secondary after
     * uninstall app successful.
     *
     * @param uninstallStatus App to be uninstalled status
     * @param packageName App to be uninstalled packagename
     * @param primaryCpuAbi App to be uninstalled primaryCpuAbi
     * @param secondaryCpuAbi App to be uninstalled secondaryCpuAbi
     *
     * @return STOP_ZYGOTE_SECONDARY_SUCESSFUL
     *  if stop zygote_secondary successful
     * @return STOP_ZYGOTE_SECONDARY_FAILED
     *  if stop zygote_secondary failed
     * @return DONOT_NEED_STOP_ZYGOTE_SECONDARY
     *  if don't need stop zygote_secondary
     * @return DONOT_CARE_ZYGOTE_SECONDARY
     *  if uninstall failed or app hasn't secondary native library
     * @return ERROR_ZYGOTE_SECONDARY
     *  if some errors occurred
     * @return UNSUPPORT_ZYGOTE_SECONDARY
     *  if unsupport zygote_secondary
     */
    private static int deleteAppZygoteSecondaryStartOrNot(
        int deleteStatus,
        String packageName,
        String primaryCpuAbi,
        String secondaryCpuAbi) {
        Integer object = null;
        int zygote_secondary = 0;
        if ((object = supportZygoteSecondaryStart()) != null) {
            zygote_secondary = object.intValue();
            if (zygote_secondary != BIT32 &&
                zygote_secondary != BIT64) {
                Log.e(TAG,
                    "variable zygote_secondary("+zygote_secondary+") != 32/64");
                return ERROR_ZYGOTE_SECONDARY;
            }
        } else {
            return UNSUPPORT_ZYGOTE_SECONDARY;
        }

        Log.i(TAG, "uninstall app: " + packageName);
        if (deleteStatus != PackageManager.DELETE_SUCCEEDED) {
            Log.e(TAG,
                "don't care zygote_secondary(Uninstall failed)");
            return DONOT_CARE_ZYGOTE_SECONDARY;
        }

        ArrayList<String> cpuAbiList =
            new ArrayList<String>();
        String [] appCpuAbi = new String [] {
            primaryCpuAbi, secondaryCpuAbi };

        if (zygote_secondary == BIT32) {
            cpuAbiList.addAll(cpu32BitAbiList);
        } else if (zygote_secondary == BIT64) {
            cpuAbiList.addAll(cpu64BitAbiList);
        }

        for (String abi : appCpuAbi) {
            if (abi != null && cpuAbiList.contains(abi)) {
                updateSecondaryNativeLibAppPackageNameList(
                    packageName, false);
                if (getSecondaryNativeLibAppPackageNameNum() != 0) {
                    Log.i(TAG, "don't need to stop zygote_secondary(Remaining " +
                        getSecondaryNativeLibAppPackageNameNum() +
                        " apps have secondary native lib)");
                    return DONOT_NEED_STOP_ZYGOTE_SECONDARY;
                } else {
                    if (isZygoteSecondaryStop()) {
                        Log.i(TAG, "zygote_secondary has stopped");
                        return STOP_ZYGOTE_SECONDARY_SUCESSFUL;
                    }

                    if (ensureZygoteSecondaryStop()) {
                        Log.i(TAG, "stop zygote_secondary successful");
                        return STOP_ZYGOTE_SECONDARY_SUCESSFUL;
                    } else {
                        Log.e(TAG, "stop zygote_secondary failed");
                        return STOP_ZYGOTE_SECONDARY_FAILED;
                    }
                }
            }
        }

        Log.i(TAG,
            "primaryCpuAbi:" + primaryCpuAbi +
            ", secondaryCpuAbi:" + secondaryCpuAbi);
        return DONOT_CARE_ZYGOTE_SECONDARY;
    }


    private static int zygoteSecondaryStart(
        PackageManagerService mService) {
        Integer object = null;
        int zygote_secondary = 0;
        if ((object = supportZygoteSecondaryStart()) != null) {
            zygote_secondary = object.intValue();
            if (zygote_secondary != BIT32 &&
                zygote_secondary != BIT64) {
                return ERROR_ZYGOTE_SECONDARY;
            }
        } else {
            return UNSUPPORT_ZYGOTE_SECONDARY;
        }

        String primaryCpuAbi = null;
        String secondaryCpuAbi = null;
        ArrayMap<String, PackageParser.Package> mPackages =
            mService.getPackages();
        ArrayList<ApplicationInfo> secondaryNativeLibAppList =
            new ArrayList<ApplicationInfo>();

        if (mPackages == null) {
            Log.e(TAG,
                "mPackages=null,can't scan app packages");
            return ERROR_ZYGOTE_SECONDARY;
        }

        Log.i(TAG, "cpu.abilist32=" + cpu32BitAbiList.toString() + ", " +
                "cpu.abilist64=" + cpu64BitAbiList.toString());

        for (PackageParser.Package pkg : mPackages.values()) {
            if (pkg == null) continue;
            primaryCpuAbi = pkg.applicationInfo.primaryCpuAbi;
            secondaryCpuAbi = pkg.applicationInfo.secondaryCpuAbi;
            if (zygote_secondary == BIT32) {
                if (cpu32BitAbiList.contains(primaryCpuAbi) ||
                    cpu32BitAbiList.contains(secondaryCpuAbi)) {
                    secondaryNativeLibAppList.add(pkg.applicationInfo);
                }
            } else if (zygote_secondary == BIT64) {
                if (cpu64BitAbiList.contains(primaryCpuAbi) ||
                    cpu64BitAbiList.contains(secondaryCpuAbi)) {
                    secondaryNativeLibAppList.add(pkg.applicationInfo);
                }
            }
        }

        Log.i(TAG, "found " +
            secondaryNativeLibAppList.size() + " apps has secondary native library");
        if (DEBUG) {
            int i = 0;
            for (ApplicationInfo app : secondaryNativeLibAppList) {
                Log.i(TAG, (++i) + ". " +
                    "packageName=" + "\"" + app.packageName + "\"" + ", " +
                    "className=" + "\"" + app.className + "\"" + ", " +
                    "codePath=" + "\"" + app.scanSourceDir + "\"" + ", " +
                    "primaryCpuAbi=" + "\"" + app.primaryCpuAbi + "\"" + ", " +
                    "secondaryCpuAbi=" + "\"" + app.secondaryCpuAbi + "\"" + ", " +
                    "nativeLibraryPath=" + "\"" + app.nativeLibraryDir + "\"" + ", " +
                    "secondaryNativeLibraryPath=" + "\"" + app.secondaryNativeLibraryDir + "\"" );
            }
        }

        setSecondaryNativeLibAppPackageNameList(secondaryNativeLibAppList);

        if (secondaryNativeLibAppList.size() > 0) {
            SystemProperties.set(PERSISIT_SYS_ZYGOTE_SECONDARY, "start");
            SystemProperties.set(SYS_ZYGOTE_SECONDARY, "start");// ensure start
            mIsZygoteSecondaryStartStatusUpdate(true);
        } else {
            SystemProperties.set(PERSISIT_SYS_ZYGOTE_SECONDARY, "stop");
            SystemProperties.set(SYS_ZYGOTE_SECONDARY, "stop");// ensure stop
            mIsZygoteSecondaryStartStatusUpdate(false);
            return STOP_ZYGOTE_SECONDARY_SUCESSFUL;
        }

        return START_ZYGOTE_SECONDARY_SUCESSFUL;
    }


    public static int installPackageZygoteSecondaryStartOrNot(
        int installStatus, ApplicationInfo app) {
        try {
            return installAppZygoteSecondaryStartOrNot(installStatus, app);
        } catch (Exception e) {
            Log.e(TAG, "abnormal on caller installAppZygoteSecondaryStartOrNot()");
            e.printStackTrace();
        }
        return handleAbnormal();
    }


    public static int deletePackageZygoteSecondaryStartOrNot(
        int deleteStatus,
        String packageName,
        String primaryCpuAbi,
        String secondaryCpuAbi) {
        try {
            return deleteAppZygoteSecondaryStartOrNot(
                deleteStatus, packageName, primaryCpuAbi, secondaryCpuAbi);
        } catch (Exception e) {
            Log.e(TAG, "abnormal on caller deleteAppZygoteSecondaryStartOrNot()");
            e.printStackTrace();
        }
        return handleAbnormal();
    }


    public static int zygoteSecondaryStartOrNot(
        PackageManagerService mService) {
        try {
            return zygoteSecondaryStart(mService);
        } catch (Exception e) {
            Log.e(TAG, "abnormal on caller zygoteSecondaryStart()");
            e.printStackTrace();
        }
        return handleAbnormal();
    }


    // If an exception occurs, start zygote_secondary
    // forced next boot in zygote64_32 or zygote32_64
    private static int handleAbnormal() {
        if (supportZygoteSecondaryStart() != null) {
            SystemProperties.set(SYS_ZYGOTE_SECONDARY, "start");
            // backup status for next boot
            if (getSecondaryNativeLibAppPackageNameNum() > 0) {
                SystemProperties.set(PERSISIT_SYS_ZYGOTE_SECONDARY, "start");
            } else {
                SystemProperties.set(PERSISIT_SYS_ZYGOTE_SECONDARY, "stop");
            }
            return START_ZYGOTE_SECONDARY_SUCESSFUL;
        }
        return UNSUPPORT_ZYGOTE_SECONDARY;
    }
}
