package com.hackqing.scale.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hackqing.scale.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore("profile")

/** Persists the scale user profile (previously in `SharedPreferences`). */
class ProfileStore(private val context: Context) {

    val profile: Flow<UserProfile> = context.profileDataStore.data.map { p ->
        UserProfile(
            userId = p[KEY_UID] ?: 1,
            heightCm = p[KEY_HEIGHT] ?: 170,
            sex = p[KEY_SEX] ?: 1,
            age = p[KEY_AGE] ?: 30,
            waistCm = p[KEY_WAIST] ?: 85,
            bustCm = p[KEY_BUST] ?: 90,
            basisWeightKg = p[KEY_BASIS] ?: 60f,
            unit = p[KEY_UNIT] ?: 1,
            bodyType = p[KEY_BODY_TYPE] ?: -1,
            heartRateSwitch = p[KEY_HR] ?: 0,
        )
    }

    suspend fun save(profile: UserProfile) {
        context.profileDataStore.edit { p ->
            p[KEY_UID] = profile.userId
            p[KEY_HEIGHT] = profile.heightCm
            p[KEY_SEX] = profile.sex
            p[KEY_AGE] = profile.age
            p[KEY_WAIST] = profile.waistCm
            p[KEY_BUST] = profile.bustCm
            p[KEY_BASIS] = profile.basisWeightKg
            p[KEY_UNIT] = profile.unit
            p[KEY_BODY_TYPE] = profile.bodyType
            p[KEY_HR] = profile.heartRateSwitch
        }
    }

    private companion object {
        val KEY_UID = intPreferencesKey("uid")
        val KEY_HEIGHT = intPreferencesKey("height")
        val KEY_SEX = intPreferencesKey("sex")
        val KEY_AGE = intPreferencesKey("age")
        val KEY_WAIST = intPreferencesKey("waist")
        val KEY_BUST = intPreferencesKey("bust")
        val KEY_BASIS = androidx.datastore.preferences.core.floatPreferencesKey("basis")
        val KEY_UNIT = intPreferencesKey("unit")
        val KEY_BODY_TYPE = intPreferencesKey("bodyType")
        val KEY_HR = intPreferencesKey("hr")
    }
}
