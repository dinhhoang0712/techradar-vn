package com.techpulse.techradar.features.messaging.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the canonical-ordering invariant directly on the value object it now lives in, independent
 * of {@code PostgresConversationRepository} — see
 * docs/adr/0009-conversation-canonical-pair-value-object.md.
 */
class ConversationParticipantsTest {

    @Test
    void of_ordersLexicographicallySmallerUuidFirst_regardlessOfArgumentOrder() {
        UUID small = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID large = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        ConversationParticipants viaSmallFirst = ConversationParticipants.of(small, large);
        ConversationParticipants viaLargeFirst = ConversationParticipants.of(large, small);

        assertThat(viaSmallFirst.userA()).isEqualTo(small);
        assertThat(viaSmallFirst.userB()).isEqualTo(large);
        assertThat(viaLargeFirst).isEqualTo(viaSmallFirst);
    }

    @Test
    void of_ordersByCanonicalStringForm_notJavaUuidCompareTo() {
        // A most-significant-bit of 0x8... makes UUID#mostSigBits negative as a SIGNED long, so
        // java.util.UUID#compareTo ranks "80..." as smaller than "70..." — the opposite of the
        // plain string/byte-wise order Postgres uses. This is exactly the disagreement the ADR
        // calls out; ConversationParticipants must follow the string form, not compareTo.
        UUID negativeSignedMsb = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID positiveSignedMsb = UUID.fromString("70000000-0000-0000-0000-000000000000");

        assertThat(negativeSignedMsb.compareTo(positiveSignedMsb))
                .as("java.util.UUID#compareTo (signed) ranks 0x80... below 0x70..., unlike string/byte order")
                .isLessThan(0);

        ConversationParticipants participants = ConversationParticipants.of(negativeSignedMsb, positiveSignedMsb);

        // Canonical STRING order (matching Postgres) puts "70..." before "80..." — the opposite of
        // what java.util.UUID#compareTo said above.
        assertThat(participants.userA()).isEqualTo(positiveSignedMsb);
        assertThat(participants.userB()).isEqualTo(negativeSignedMsb);
    }

    @Test
    void of_rejectsSameUserTwice() {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> ConversationParticipants.of(user, user))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
