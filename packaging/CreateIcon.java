package com.gmricker.monitor.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.file.*;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** Build-only ICO encoder using the same vector artwork as the tray and title bar. */
public final class CreateIcon {
    public static void main(String[] args) throws Exception {
        var images = ApplicationIcon.windowImages();
        var pngs = new ArrayList<byte[]>();
        for (var image : images) {
            var png = new ByteArrayOutputStream();
            ImageIO.write((BufferedImage) image, "png", png);
            pngs.add(png.toByteArray());
        }
        int offset = 6 + 16 * images.size();
        var output = ByteBuffer.allocate(offset + pngs.stream().mapToInt(bytes -> bytes.length).sum()).order(ByteOrder.LITTLE_ENDIAN);
        output.putShort((short) 0).putShort((short) 1).putShort((short) images.size());
        for (int i = 0; i < images.size(); i++) {
            var image = images.get(i);
            output.put((byte) image.getWidth(null)).put((byte) image.getHeight(null)).put((byte) 0).put((byte) 0);
            output.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(output::put);
        var path = Path.of(args[0]);
        Files.createDirectories(path.getParent());
        Files.write(path, output.array());
    }
}
