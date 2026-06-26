package com.techpulse.techradar.features.radar.adapters.input;

import com.techpulse.techradar.features.radar.domain.TechSnapshot;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders radar trends to downloadable CSV and PNG artifacts.
 * PNG uses headless AWT (Spring Boot runs with {@code java.awt.headless=true}), so no extra dependency is needed.
 */
@Component
public class RadarExporter {

    public byte[] toCsv(List<TechSnapshot> trends) {
        StringBuilder sb = new StringBuilder();
        sb.append("technology_name,job_count,growth_rate\n");
        if (trends != null) {
            for (TechSnapshot t : trends) {
                sb.append(csv(t.name())).append(',')
                        .append(t.jobCount()).append(',')
                        .append(t.growthRate()).append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] toPng(List<TechSnapshot> trends) {
        int width = 900;
        int rowHeight = 38;
        int topPad = 70;
        int bottomPad = 30;
        int count = trends == null ? 0 : trends.size();
        int height = topPad + bottomPad + Math.max(count, 1) * rowHeight;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            g.setColor(new Color(0x1F2937));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString("Top Technologies by Growth Rate", 24, 38);

            if (count == 0) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 16));
                g.setColor(Color.GRAY);
                g.drawString("No data available", 24, topPad);
                g.dispose();
                return encode(image);
            }

            int labelWidth = 220;
            int barX = labelWidth + 24;
            int barMaxWidth = width - barX - 90;

            double maxGrowth = 0.0;
            for (TechSnapshot t : trends) {
                maxGrowth = Math.max(maxGrowth, Math.abs(t.growthRate()));
            }
            if (maxGrowth <= 0.0) {
                maxGrowth = 1.0;
            }

            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            for (int i = 0; i < count; i++) {
                TechSnapshot t = trends.get(i);
                int y = topPad + i * rowHeight;

                g.setColor(new Color(0x374151));
                String label = t.name() == null ? "(unknown)" : t.name();
                g.drawString(truncate(label, 26), 24, y + 22);

                int barWidth = (int) Math.round((Math.abs(t.growthRate()) / maxGrowth) * barMaxWidth);
                g.setColor(new Color(0x2563EB));
                g.fillRoundRect(barX, y + 8, Math.max(barWidth, 2), 20, 8, 8);

                g.setColor(new Color(0x111827));
                g.drawString(String.format("%.2f", t.growthRate()), barX + Math.max(barWidth, 2) + 8, y + 22);
            }

            g.setColor(new Color(0xD1D5DB));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(barX, topPad - 6, barX, topPad + count * rowHeight);

            g.dispose();
            return encode(image);
        } catch (RuntimeException ex) {
            g.dispose();
            throw ex;
        }
    }

    private byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render radar PNG", e);
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
