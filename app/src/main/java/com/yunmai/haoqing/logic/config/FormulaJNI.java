package com.yunmai.haoqing.logic.config;

/**
 * 直接复用云麦/好轻 App 自带的体成分算法原生库 libconfig.so。
 *
 * JNI 采用「类名 + 方法名」静态绑定，因此本类的全限定名与方法签名必须与原 App 完全一致，
 * 否则符号无法解析。libconfig.so 从 yunmai-base.apk (v5.6.4) 中提取，放于 jniLibs。
 */
public class FormulaJNI {

    static {
        System.loadLibrary("config");
    }

    /** 由身高/年龄/性别/体重/阻抗推算体脂率。 */
    public static native float formFat(int height, int age, int sex, float weight,
                                       float resistance, int version, int extra,
                                       String locale, String type);

    /** 底层：返回体成分数组。 */
    public static native float[] getValue(int height, int age, int sex, float weight, float fat,
                                          float resistance, int version, int extra,
                                          String locale, String type);

    /** 与原 App 相同的封装：locale=zh, extra=0。
     *  注意：实测参数顺序为 (身高, 年龄, 性别, 体重, 阻抗, 体脂, 版本, 类型)，
     *  即第 5 个 float 为阻抗、第 6 个为体脂率（jadx 的参数命名有误）。 */
    public static float[] formValue(int height, int age, int sex, float weight, float resistance,
                                    float fat, int version, String type) {
        return getValue(height, age, sex, weight, resistance, fat, version, 0, "zh", type);
    }
}
