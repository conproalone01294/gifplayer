#include "gif.h"

void cleanUp(GifInfo *info) {
	free(info->backupPtr);
	info->backupPtr = NULL;
	free(info->controlBlock);
	info->controlBlock = NULL;
	free(info->rasterBits);
	info->rasterBits = NULL;
	free(info->comment);
	info->comment = NULL;

	DGifCloseFile(info->gifFilePtr);
	free(info);
}

GifInfo *createGifHandle(GifSourceDescriptor *descriptor, JNIEnv *env, jboolean justDecodeMetaData, jint maxSize) {
	if (descriptor->startPos < 0) {
		descriptor->Error = D_GIF_ERR_NOT_READABLE;
		DGifCloseFile(descriptor->GifFileIn);
	}
	if (descriptor->Error != 0 || descriptor->GifFileIn == NULL) {
		throwGifIOException(descriptor->Error, env);
		return NULL;
	}

	GifInfo *info = malloc(sizeof(GifInfo));
	if (info == NULL) {
		DGifCloseFile(descriptor->GifFileIn);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL;
	}
	info->controlBlock = malloc(sizeof(GraphicsControlBlock));
	if (info->controlBlock == NULL) {
		DGifCloseFile(descriptor->GifFileIn);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL;
	}
	setGCBDefaults(info->controlBlock);
	info->destructor = NULL;
	info->gifFilePtr = descriptor->GifFileIn;
	info->startPos = descriptor->startPos;
	info->currentIndex = 0;
	info->nextStartTime = 0;
	info->lastFrameRemainder = -1;
	info->comment = NULL;
	info->loopCount = 1;
	info->currentLoop = 0;
	info->speedFactor = 1.0;
	info->sourceLength = descriptor->sourceLength;

	info->backupPtr = NULL;
	info->rewindFunction = descriptor->rewindFunc;
	info->frameBufferDescriptor = NULL;
	info->isOpaque = false;
	info->sampleSize = 1;

	DDGifSlurp(info, false, false);
	// Check whether size from header is bigger than maxSize or not.
    if (descriptor->GifFileIn->SHeight * descriptor->GifFileIn->SWidth * 4 > maxSize) {
        descriptor->GifFileIn->Error = D_GIF_ERR_EXCEED_SIZE_LIMIT;
        throwGifIOException(D_GIF_ERR_EXCEED_SIZE_LIMIT, env);
        return NULL;
    }

	if (justDecodeMetaData == JNI_TRUE) {
		info->rasterBits = NULL;
	} else {
		info->rasterBits = malloc(
				descriptor->GifFileIn->SHeight * descriptor->GifFileIn->SWidth * sizeof(GifPixelType));
		if (info->rasterBits == NULL) {
			descriptor->GifFileIn->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
		}
	}
	info->originalHeight = info->gifFilePtr->SHeight;
	info->originalWidth = info->gifFilePtr->SWidth;

	if (descriptor->GifFileIn->SWidth < 1 || descriptor->GifFileIn->SHeight < 1) {
		DGifCloseFile(descriptor->GifFileIn);
		throwGifIOException(D_GIF_ERR_INVALID_SCR_DIMS, env);
		return NULL;
	}
	if (descriptor->GifFileIn->Error == D_GIF_ERR_NOT_ENOUGH_MEM) {
		cleanUp(info);
		throwException(env, OUT_OF_MEMORY_ERROR, OOME_MESSAGE);
		return NULL;
	}
#if defined(STRICT_FORMAT_89A)
	descriptor->Error = descriptor->GifFileIn->Error;
#endif

	if (descriptor->GifFileIn->ImageCount == 0) {
		descriptor->Error = D_GIF_ERR_NO_FRAMES;
	}
	else if (descriptor->GifFileIn->Error == D_GIF_ERR_REWIND_FAILED) {
		descriptor->Error = D_GIF_ERR_REWIND_FAILED;
	}
	if (descriptor->Error != 0) {
		cleanUp(info);
		throwGifIOException(descriptor->Error, env);
		return NULL;
	}
	return info;
}

void setGCBDefaults(GraphicsControlBlock *gcb) {
	gcb->DelayTime = DEFAULT_FRAME_DURATION_MS;
	gcb->TransparentColor = NO_TRANSPARENT_COLOR;
	gcb->DisposalMode = DISPOSAL_UNSPECIFIED;
}
