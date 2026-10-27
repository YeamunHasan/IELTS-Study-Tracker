package com.ielts.tracker.app;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Draws the window and tray icon in code, so there is no image file to ship. */
final class AppIcon {
    private AppIcon() {}

    static BufferedImage awt(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x5B6BE0));
            g.fill(new RoundRectangle2D.Float(0, 0, size, size, size * 0.28f, size * 0.28f));
            g.setColor(Color.WHITE);
            float ring = size * 0.09f;
            g.setStroke(new BasicStroke(ring, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float inset = size * 0.2f;
            g.draw(new Ellipse2D.Float(inset, inset, size - 2 * inset, size - 2 * inset));
            g.setStroke(new BasicStroke(ring * 0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float c = size / 2f;
            g.drawLine(Math.round(c), Math.round(c), Math.round(c), Math.round(size * 0.33f));
            g.drawLine(Math.round(c), Math.round(c), Math.round(size * 0.64f), Math.round(c));
        } finally {
            g.dispose();
        }
        return img;
    }

    static Image fx(int size) {
        BufferedImage src = awt(size);
        WritableImage out = new WritableImage(size, size);
        PixelWriter w = out.getPixelWriter();
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) w.setArgb(x, y, src.getRGB(x, y));
        return out;
    }
}
