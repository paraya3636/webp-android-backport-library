package de.marcreichelt.webp_backport;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Helper for loading WebP images on old and new Android platforms. Loads a native decoding library
 * 'libwebp' when the current Android version does not support WebP.
 */
public class WebPBackport {

    private static final String TAG = WebPBackport.class.getSimpleName();
    private static final int BYTES_PER_PIXEL_ARGB_8888 = 4;
    static boolean librarySuccessfullyLoaded = false;

    static {
        if (!isWebpSupportedNatively()) {
            loadLibrary();
        }
    }

    public static void forceLoadLibrary() {
        loadLibrary();
    }

    private static native boolean getInfo(byte[] encoded, int[] width, int[] height);

    private static native boolean decodeRGBAInto(Bitmap bitmap, byte[] encoded);

    static void loadLibrary() {
        try {
            System.loadLibrary("webpbackport");
            librarySuccessfullyLoaded = true;
        } catch (Exception e) {
            Log.w(TAG, "failed to load webp library", e);
        }
    }

    static boolean isWebpSupportedNatively() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    }


    /**
     * Decodes any image supported by the system or the WebP library.
     * <p>Note that this method not only supports WebP, but supports PNG, JPG etc. as well. But if
     * WebP data is detected this will do some more magic. :-)</p>
     *
     * @param encoded The encoded image data (e.g. from a stream, resource, etc.).
     * @return The decoded image, or {@code null} if it could not be decoded successfully.
     */
    public static Bitmap decode(byte[] encoded) {
        if (isLibraryUsed() && FileSignatureChecker.checkForWebP(encoded)) {
            return decodeViaLibrary(encoded);
        }
        return decodeViaSystem(encoded);
    }

    /**
     * Returns whether WebP images can be decoded. More exact: if WebP is supported by the platform
     * or by the included native library.
     *
     * @return {@code true} if WebP is supported.
     */
    public static boolean isWebPSupported() {
        return isWebpSupportedNatively() || librarySuccessfullyLoaded;
    }

    /**
     * Is the native library used to decode WebP images?
     *
     * @return {@code true} if the native library is used.
     */
    public static boolean isLibraryUsed() {
        return librarySuccessfullyLoaded;
    }

    static Bitmap decodeViaLibrary(byte[] encoded) {
        return decodeViaLibrary(encoded, null);
    }

    /**
     * Get size of an encoded WebP image.
     *
     * @param encoded The WebP data.
     * @return The size of the WebP image, of {@code null} if something went wrong.
     */
    @Nullable
    public static Rect getSize(byte[] encoded) {
        if (encoded == null) {
            return null;
        }

        int[] width = {0};
        int[] height = {0};
        boolean result = getInfo(encoded, width, height);
        return result ? new Rect(0, 0, width[0], height[0]) : null;
    }

    @Nullable
    static Bitmap decodeViaLibrary(byte[] encoded, Bitmap reusableBitmap) {
        Bitmap buffer = useOrCreateBuffer(encoded, reusableBitmap);
        if (buffer == null) {
            return null;
        }

        int[] width = {0};
        int[] height = {0};
        boolean result = getInfo(encoded, width, height);
        if (!result) {
            Log.w(TAG, "unable to determine size of WebP image");
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(width[0], height[0], Config.ARGB_8888);
        decodeRGBAInto(bitmap, encoded);
        return bitmap;
    }

    @Nullable
    private static Bitmap useOrCreateBuffer(byte[] encoded, Bitmap buffer) {
        if (encoded == null) {
            return null;
        }

        int[] width = new int[]{0};
        int[] height = new int[]{0};
        boolean result = getInfo(encoded, width, height);

        if (!result || width[0] <= 0 || height[0] <= 0) {
            return null;
        }

        if (buffer != null) {
            int sizeNeeded = width[0] * height[0] * BYTES_PER_PIXEL_ARGB_8888;
            int bufferBytes = buffer.getRowBytes() * buffer.getHeight();
            if (bufferBytes < sizeNeeded) {
                throw new IllegalArgumentException("buffer of " + bufferBytes
                        + " bytes is not big enough for bitmap of " + sizeNeeded + " bytes");
            }
            if (!buffer.getConfig().equals(Config.ARGB_8888)) {
                throw new IllegalArgumentException("buffer has to be of type ARGB_8888");
            }
        } else {
            buffer = Bitmap.createBitmap(width[0], height[0], Config.ARGB_8888);
        }

        return buffer;
    }

    @Nullable
    static Bitmap decodeViaSystem(byte[] encoded) {
        return BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
    }

}
