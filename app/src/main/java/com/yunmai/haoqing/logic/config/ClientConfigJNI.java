package com.yunmai.haoqing.logic.config;

import android.content.Context;

/** libconfig.so 的初始化入口（与原 App 一致，供算法库加载配置）。 */
public class ClientConfigJNI {

    static {
        System.loadLibrary("config");
    }

    public static native void init(Context context);
}
