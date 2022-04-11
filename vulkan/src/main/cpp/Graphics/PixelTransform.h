#ifndef HAISHINKIT_KT_PIXELTRANSFORM_H
#define HAISHINKIT_KT_PIXELTRANSFORM_H

#include <jni.h>
#include <android/hardware_buffer_jni.h>
#include <media/NdkImageReader.h>
#include "Kernel.h"
#include "SurfaceRotation.hpp"
#include "ResampleFilter.h"

namespace Graphics {

    class PixelTransform {
    public:
        static void OnImageAvailable(void *ctx, AImageReader *reader);

        AImageReader *imageReader;

        PixelTransform();

        ~PixelTransform();

        bool IsReady();

        void SetImageReader(int32_t width, int32_t height, int32_t format);

        void SetVideoGravity(VideoGravity newVideoGravity);

        void SetResampleFilter(ResampleFilter newResampleFilter);

        void SetAssetManager(AAssetManager *assetManager);

        void SetNativeWindow(ANativeWindow *nativeWindow);

        void SetImageOrientation(ImageOrientation imageOrientation);

        void SetSurfaceRotation(SurfaceRotation surfaceRotation);

        void ReadPixels(void *byteBuffer);

        void OnImageAvailable(AImageReader *reader);

        std::string InspectDevices();

    private:
        ANativeWindow *nativeWindow;
        std::vector<Texture *> textures;
        Kernel *kernel;
        VideoGravity videoGravity = RESIZE_ASPECT_FILL;
        ResampleFilter resampleFilter = LINEAR;
        ImageOrientation imageOrientation = UP;
    };
}

#endif //HAISHINKIT_KT_PIXELTRANSFORM_H