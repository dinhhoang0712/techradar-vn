package com.techpulse.techradar.features.social.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashtagParserTest {

    @Test
    void parse_returnsEmptyListForBlankOrNullContent() {
        assertThat(HashtagParser.parse(null)).isEmpty();
        assertThat(HashtagParser.parse("")).isEmpty();
        assertThat(HashtagParser.parse("   ")).isEmpty();
        assertThat(HashtagParser.parse("no hashtags here")).isEmpty();
    }

    @Test
    void parse_extractsAndLowercasesSimpleTags() {
        assertThat(HashtagParser.parse("Học #Java và #ReactJS hôm nay")).containsExactly("java", "reactjs");
    }

    @Test
    void parse_dedupesCaseInsensitivelyPreservingFirstSeenOrder() {
        assertThat(HashtagParser.parse("#Java #java #JAVA #python")).containsExactly("java", "python");
    }

    @Test
    void parse_matchesVietnameseDiacriticLetters() {
        assertThat(HashtagParser.parse("Xu hướng #côngNghệ và #trítuệNhânTạo")).containsExactly("côngnghệ", "trítuệnhântạo");
    }

    @Test
    void parse_rejectsATagThatDoesNotStartWithALetter() {
        assertThat(HashtagParser.parse("#123 #_underscore not real tags")).isEmpty();
    }

    @Test
    void parse_allowsDigitsAndUnderscoresAfterTheFirstLetter() {
        assertThat(HashtagParser.parse("#java_8 #web3")).containsExactly("java_8", "web3");
    }

    @Test
    void parse_stopsAtNonWordCharacters() {
        assertThat(HashtagParser.parse("Tuyệt vời! #java, #python.")).containsExactly("java", "python");
    }

    @Test
    void parse_capsAtTwentyHashtagsPerPost() {
        String content = IntStream.range(0, 30).mapToObj(i -> "#tag" + i).collect(Collectors.joining(" "));
        assertThat(HashtagParser.parse(content)).hasSize(20).containsExactly(
                IntStream.range(0, 20).mapToObj(i -> "tag" + i).toArray(String[]::new));
    }

    @Test
    void parse_handlesNfdDecomposedVietnameseDiacriticsTheSameAsNfc() {
        // "ệ" as a single NFC codepoint vs. "e" + combining marks (NFD) must parse identically.
        String nfc = "#côngNghệ";
        String nfd = java.text.Normalizer.normalize(nfc, java.text.Normalizer.Form.NFD);
        assertThat(HashtagParser.parse(nfd)).isEqualTo(HashtagParser.parse(nfc));
    }

    @Test
    void parse_ignoresABareHashWithNothingAfterIt() {
        assertThat(HashtagParser.parse("price is # 5 dollars")).isEmpty();
    }

    @Test
    void parse_returnsImmutableList() {
        List<String> tags = HashtagParser.parse("#java");
        assertThatThrownBy(() -> tags.add("python")).isInstanceOf(UnsupportedOperationException.class);
    }
}
