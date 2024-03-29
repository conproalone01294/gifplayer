#include "gif.h"

__unused JNIEXPORT jstring JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getComment(JNIEnv *env, jclass __unused handleClass,
                                                   jlong gifInfo) {
	if (gifInfo == 0) {
		return NULL;
	}
	return (*env)->NewStringUTF(env, ((GifInfo *) (intptr_t) gifInfo)->comment);
}

__unused JNIEXPORT jboolean JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_isAnimationCompleted(JNIEnv __unused *env, jclass __unused handleClass,
                                                             jlong gifInfo) {
	GifInfo *info = ((GifInfo *) (intptr_t) gifInfo);
	if (info != NULL && info->loopCount != 0 && info->currentLoop == info->loopCount)
		return JNI_TRUE;
	else
		return JNI_FALSE;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getLoopCount(JNIEnv __unused *env, jclass __unused handleClass,
                                                     jlong gifInfo) {
	if (gifInfo == 0)
		return 0;
	return (jint) ((GifInfo *) (intptr_t) gifInfo)->loopCount;
}

__unused JNIEXPORT void JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_setLoopCount(JNIEnv __unused *env, jclass __unused handleClass,
                                                     jlong gifInfo, jint loopCount) {
	if (gifInfo != 0)
		((GifInfo *) (intptr_t) gifInfo)->loopCount = (uint_fast16_t) loopCount;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getDuration(JNIEnv *__unused  env, jclass __unused handleClass,
                                                    jlong gifInfo) {
	if (gifInfo == 0) {
		return 0;
	}
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	uint_fast32_t i;
	jint sum = 0;
	for (i = 0; i < info->gifFilePtr->ImageCount; i++)
		sum += info->controlBlock[i].DelayTime;
	return sum;
}

__unused JNIEXPORT jlong JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getSourceLength(JNIEnv __unused *env, jclass __unused handleClass,
                                                        jlong gifInfo) {
	if (gifInfo == 0) {
		return -1;
	}
	return ((GifInfo *) (intptr_t) gifInfo)->sourceLength;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getCurrentPosition(JNIEnv *__unused env,
                                                           jclass __unused handleClass, jlong gifInfo) {
	if (gifInfo == 0) {
		return 0;
	}
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	const uint_fast32_t idx = info->currentIndex;
	if (info->gifFilePtr->ImageCount == 1) {
		return 0;
	}
	uint_fast32_t i;
	uint32_t sum = 0;
	for (i = 0; i < idx; i++) {
		sum += info->controlBlock[i].DelayTime;
	}

	long long remainder;
	if (info->lastFrameRemainder == -1) {
		remainder = info->nextStartTime - getRealTime();
		if (remainder < 0) { //in case of if frame hasn't been rendered until nextStartTime passed
			remainder = 0;
		}
	}
	else {
		remainder = info->lastFrameRemainder;
	}
	return (jint) (sum - remainder); //2^31-1[ms]>596[h] so jint is enough
}

__unused JNIEXPORT jlong JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getAllocationByteCount(JNIEnv *__unused  env,
                                                               jclass __unused handleClass, jlong gifInfo) {
	if (gifInfo == 0) {
		return 0;
	}
	GifInfo *info = (GifInfo *) (intptr_t) gifInfo;
	GifWord pxCount = info->originalWidth + info->originalHeight;
	size_t sum = pxCount * sizeof(char);
	if (info->backupPtr != NULL)
		sum += pxCount * sizeof(argb);
	return (jlong) sum;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getNativeErrorCode(JNIEnv *__unused  env,
                                                           jclass __unused handleClass, jlong gifInfo) {
	if (gifInfo == 0)
		return 0;
	return ((GifInfo *) (intptr_t) gifInfo)->gifFilePtr->Error;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getCurrentLoop(JNIEnv __unused *env, jclass __unused handleClass,
                                                       jlong gifInfo) {
	if (gifInfo == 0)
		return 0;
	return (jint) ((GifInfo *) (intptr_t) gifInfo)->currentLoop;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getCurrentFrameIndex(JNIEnv __unused *env, jclass __unused handleClass,
                                                             jlong gifInfo) {
	if (gifInfo == 0)
		return -1;
	return (jint) ((GifInfo *) (intptr_t) gifInfo)->currentIndex;
}

__unused JNIEXPORT jlongArray JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getSavedState(JNIEnv *env, jclass __unused handleClass,
                                                      jlong gifInfo) {
	GifInfo *const info = ((GifInfo *) (intptr_t) gifInfo);
	if (info == NULL)
		return NULL;

	const jlongArray state = (*env)->NewLongArray(env, 4);
	if (state == NULL) {
		throwException(env, RUNTIME_EXCEPTION_BARE, "Could not create state array");
		return NULL;
	}
	jlong nativeState[4] = {(jlong) info->currentIndex, (jlong) info->currentLoop, info->lastFrameRemainder};
	memcpy(nativeState + 3, &info->speedFactor, sizeof(info->speedFactor));
	(*env)->SetLongArrayRegion(env, state, 0, 4, nativeState);
	return state;
}

jint restoreSavedState(GifInfo *info, JNIEnv *env, jlongArray state, void *pixels) {
	if (info->gifFilePtr->ImageCount == 1) {
		return -1;
	}

	jlong nativeState[4];
	(*env)->GetLongArrayRegion(env, state, 0, 4, nativeState);

	const uint_fast32_t savedIndex = (uint_fast32_t) nativeState[0];
	const uint_fast8_t savedLoop = (uint_fast8_t) nativeState[1];

	if (savedIndex >= info->gifFilePtr->ImageCount || info->currentLoop > info->loopCount)
		return -1;

	if (savedIndex < info->currentIndex && !reset(info)) {
		info->gifFilePtr->Error = D_GIF_ERR_REWIND_FAILED;
		return -1;
	}

	uint_fast32_t lastFrameDuration = info->controlBlock[info->currentIndex].DelayTime;
	if (info->currentIndex < savedIndex) {
		if (info->currentIndex == 0)
			prepareCanvas(pixels, info);
		while (info->currentIndex < savedIndex) {
			DDGifSlurp(info, true, false);
			lastFrameDuration = getBitmap((argb *) pixels, info);
		}
	}

	info->currentLoop = savedLoop;
	info->lastFrameRemainder = nativeState[2];
	memcpy(&info->speedFactor, nativeState + 3, sizeof(info->speedFactor));

	if (info->lastFrameRemainder == -1) {
		uint_fast32_t duration = (uint_fast32_t) (lastFrameDuration * info->speedFactor);
		info->nextStartTime = getRealTime() + duration;
		return (jint) duration;
	}
	return -1;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_restoreSavedState(JNIEnv *env, jclass __unused handleClass,
                                                          jlong gifInfo, jlongArray state, jobject jbitmap) {
	GifInfo *const info = ((GifInfo *) (intptr_t) gifInfo);
	void *pixels;
	if (info == NULL || lockPixels(env, jbitmap, info, &pixels) != 0) {
		return -1;
	}
	const jint invalidationDelay = restoreSavedState(info, env, state, pixels);
	unlockPixels(env, jbitmap);
	return invalidationDelay;
}

__unused JNIEXPORT jint JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_getFrameDuration(__unused JNIEnv *env, jclass __unused handleClass, jlong gifInfo, jint index) {
	GifInfo *const info = ((GifInfo *) (intptr_t) gifInfo);
	return info == NULL ? 0 : (jint) info->controlBlock[index].DelayTime;
}

__unused JNIEXPORT jboolean JNICALL
Java_com_zing_zalo_gifplayer_GifInfoHandle_isOpaque(__unused JNIEnv *env, jclass __unused handleClass, jlong gifInfo) {
	GifInfo *const info = ((GifInfo *) (intptr_t) gifInfo);
	if (info != NULL && info->isOpaque) {
		return JNI_TRUE;
	}
	return JNI_FALSE;
}
