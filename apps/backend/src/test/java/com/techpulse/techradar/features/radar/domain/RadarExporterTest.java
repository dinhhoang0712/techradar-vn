package com.techpulse.techradar.features.radar.domain;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link RadarExporter} is a pure renderer (no I/O, no Spring web layer beyond {@code @Component}),
 * so these tests need no mocks: just feed {@link TechSnapshot} lists in and assert on the bytes out.
 */
class RadarExporterTest {

    private final RadarExporter exporter = new RadarExporter();

    // ---- toCsv -----------------------------------------------------------

    @Test
    void toCsv_nullList_producesOnlyTheHeaderRow() {
        String csv = new String(exporter.toCsv(null), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\n");
    }

    @Test
    void toCsv_emptyList_producesOnlyTheHeaderRow() {
        String csv = new String(exporter.toCsv(List.of()), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\n");
    }

    @Test
    void toCsv_singleEntry_hasHeaderAndOneCorrectRow() {
        TechSnapshot snapshot = new TechSnapshot("Kotlin", 120, 45.5, 30.0, 40);

        String csv = new String(exporter.toCsv(List.of(snapshot)), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\nKotlin,120,45.5\n");
    }

    @Test
    void toCsv_multipleEntries_hasHeaderAndOneRowPerEntryInOrder() {
        TechSnapshot first = new TechSnapshot("Kotlin", 120, 45.5, 30.0, 40);
        TechSnapshot second = new TechSnapshot("Rust", 80, -5.25, 1.0, 10);

        String csv = new String(exporter.toCsv(List.of(first, second)), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo(
                "technology_name,job_count,growth_rate\n"
                        + "Kotlin,120,45.5\n"
                        + "Rust,80,-5.25\n");
    }

    @Test
    void toCsv_nameContainingComma_isQuoted() {
        TechSnapshot snapshot = new TechSnapshot("C, the language", 10, 1.0, 1.0, 1);

        String csv = new String(exporter.toCsv(List.of(snapshot)), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\n\"C, the language\",10,1.0\n");
    }

    @Test
    void toCsv_nameContainingDoubleQuote_isQuotedAndQuotesAreEscapedByDoubling() {
        TechSnapshot snapshot = new TechSnapshot("The \"best\" lang", 10, 1.0, 1.0, 1);

        String csv = new String(exporter.toCsv(List.of(snapshot)), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\n\"The \"\"best\"\" lang\",10,1.0\n");
    }

    @Test
    void toCsv_nameContainingNewline_isQuoted() {
        TechSnapshot snapshot = new TechSnapshot("Multi\nline", 10, 1.0, 1.0, 1);

        String csv = new String(exporter.toCsv(List.of(snapshot)), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\n\"Multi\nline\",10,1.0\n");
    }

    @Test
    void toCsv_nameWithNoSpecialCharacters_isNotQuoted() {
        TechSnapshot snapshot = new TechSnapshot("PlainName", 10, 1.0, 1.0, 1);

        String csv = new String(exporter.toCsv(List.of(snapshot)), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\nPlainName,10,1.0\n");
    }

    @Test
    void toCsv_nullTechnologyName_rendersAsEmptyField() {
        TechSnapshot snapshot = new TechSnapshot(null, 10, 1.0, 1.0, 1);

        String csv = new String(exporter.toCsv(List.of(snapshot)), StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("technology_name,job_count,growth_rate\n,10,1.0\n");
    }

    // ---- toPng -------------------------------------------------------------

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    @Test
    void toPng_nonEmptyList_returnsBytesStartingWithThePngMagicNumber() {
        byte[] png = exporter.toPng(List.of(new TechSnapshot("Kotlin", 120, 45.0, 30.0, 40)));

        assertThat(png).isNotEmpty();
        assertThat(java.util.Arrays.copyOf(png, 4)).isEqualTo(PNG_MAGIC);
    }

    @Test
    void toPng_nonEmptyList_decodesToAnImageAndHeightGrowsWithRowCount() throws IOException {
        byte[] onePng = exporter.toPng(List.of(new TechSnapshot("Kotlin", 120, 45.0, 30.0, 40)));
        byte[] threePng = exporter.toPng(List.of(
                new TechSnapshot("Kotlin", 120, 45.0, 30.0, 40),
                new TechSnapshot("Rust", 80, 12.0, 5.0, 10),
                new TechSnapshot("Go", 60, 8.0, 2.0, 5)));

        BufferedImage oneRow = ImageIO.read(new ByteArrayInputStream(onePng));
        BufferedImage threeRows = ImageIO.read(new ByteArrayInputStream(threePng));

        assertThat(oneRow).isNotNull();
        assertThat(threeRows).isNotNull();
        assertThat(oneRow.getWidth()).isEqualTo(900);
        assertThat(threeRows.getWidth()).isEqualTo(900);
        // height = topPad(70) + bottomPad(30) + count * rowHeight(38)
        assertThat(oneRow.getHeight()).isEqualTo(70 + 30 + 1 * 38);
        assertThat(threeRows.getHeight()).isEqualTo(70 + 30 + 3 * 38);
        assertThat(threeRows.getHeight()).isGreaterThan(oneRow.getHeight());
    }

    @Test
    void toPng_nullList_rendersAValidNoDataPlaceholderImageWithoutThrowing() throws IOException {
        byte[] png = exporter.toPng(null);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(900);
        // count == 0 -> height = topPad(70) + bottomPad(30) + max(0,1) * rowHeight(38)
        assertThat(image.getHeight()).isEqualTo(70 + 30 + 1 * 38);
    }

    @Test
    void toPng_emptyList_rendersAValidNoDataPlaceholderImageWithoutThrowing() throws IOException {
        byte[] png = exporter.toPng(List.of());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(image).isNotNull();
        assertThat(java.util.Arrays.copyOf(png, 4)).isEqualTo(PNG_MAGIC);
    }

    @Test
    void toPng_veryLongTechnologyName_doesNotThrowAndStillProducesAValidImage() {
        String veryLongName = "A".repeat(200) + " Extremely Long Technology Name That Must Be Truncated";
        TechSnapshot snapshot = new TechSnapshot(veryLongName, 10, 5.0, 1.0, 1);

        assertThatCode(() -> {
            byte[] png = exporter.toPng(List.of(snapshot));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    void toPng_nullTechnologyName_doesNotThrowAndStillProducesAValidImage() {
        TechSnapshot snapshot = new TechSnapshot(null, 10, 5.0, 1.0, 1);

        assertThatCode(() -> {
            byte[] png = exporter.toPng(List.of(snapshot));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    void toPng_allZeroGrowthRates_doesNotDivideByZeroAndStillProducesAValidImage() {
        TechSnapshot snapshot = new TechSnapshot("Flatline", 10, 0.0, 0.0, 1);

        assertThatCode(() -> {
            byte[] png = exporter.toPng(List.of(snapshot));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image).isNotNull();
        }).doesNotThrowAnyException();
    }
}
