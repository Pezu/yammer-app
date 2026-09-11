package com.yammer.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Renders QR codes as PNG images (ZXing). */
@Service
public class QrCodeService {

    /** PNG bytes of a QR encoding {@code text}, {@code size} px square (black on white). */
    public byte[] png(String text, int size) {
        return png(text, size, 0xFF000000, 0xFFFFFFFF);
    }

    /**
     * PNG bytes of a QR with the given ARGB module ({@code on}) and background ({@code off})
     * colours — e.g. white modules on a transparent background for dark artwork.
     */
    public byte[] png(String text, int size, int on, int off) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos, new MatrixToImageConfig(on, off));
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to render QR code", e);
        }
    }
}
