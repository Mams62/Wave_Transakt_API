package com.wavetransakt.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeGeneratorService {

    public byte[] generatePng(String content, int width, int height) {

        try {

            Map<EncodeHintType, Object> hints = new HashMap<>();

            hints.put(
                    EncodeHintType.MARGIN,
                    1
            );

            BitMatrix matrix =
                    new MultiFormatWriter().encode(
                            content,
                            BarcodeFormat.QR_CODE,
                            width,
                            height,
                            hints
                    );

            int matrixWidth = matrix.getWidth();
            int matrixHeight = matrix.getHeight();

            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(
                            matrixWidth,
                            matrixHeight,
                            java.awt.image.BufferedImage.TYPE_INT_RGB
                    );

            for (int x = 0; x < matrixWidth; x++) {

                for (int y = 0; y < matrixHeight; y++) {

                    image.setRGB(
                            x,
                            y,
                            matrix.get(x, y)
                                    ? 0xFF000000
                                    : 0xFFFFFFFF
                    );
                }
            }

            java.io.ByteArrayOutputStream output =
                    new java.io.ByteArrayOutputStream();

            javax.imageio.ImageIO.write(
                    image,
                    "PNG",
                    output
            );

            return output.toByteArray();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to generate QR code",
                    e
            );
        }
    }
}