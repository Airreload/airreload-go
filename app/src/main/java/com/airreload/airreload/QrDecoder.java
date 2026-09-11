package com.airreload.airreload;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

final class QrDecoder {
  private QrDecoder() {}

  static String decode(Bitmap bitmap) throws NotFoundException {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
    hints.put(DecodeHintType.TRY_HARDER, true);
    MultiFormatReader reader = new MultiFormatReader();
    try {
      return reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText();
    } catch (NotFoundException exception) {
      return reader.decode(new BinaryBitmap(new HybridBinarizer(source.invert())), hints).getText();
    }
  }
}

