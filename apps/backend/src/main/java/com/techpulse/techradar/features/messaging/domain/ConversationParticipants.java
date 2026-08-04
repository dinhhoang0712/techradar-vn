package com.techpulse.techradar.features.messaging.domain;

import java.util.UUID;

/**
 * The canonical {@code (userA, userB)} pair identifying a 1-1 conversation, ordered so the same 2
 * users always resolve to the same pair regardless of who calls {@link #of} first — this is what
 * lets Postgres's {@code UNIQUE(user_a_id, user_b_id)} / {@code CHECK (user_a_id < user_b_id)}
 * constraint on the {@code conversation} table de-duplicate a conversation no matter which side
 * starts it. Previously this ordering lived inline inside
 * {@code PostgresConversationRepository.findOrCreate} with no reusable concept to anchor it — see
 * {@code docs/adr/0009-conversation-canonical-pair-value-object.md}.
 */
public record ConversationParticipants(UUID userA, UUID userB) {

    public static ConversationParticipants of(UUID userX, UUID userY) {
        if (userX.equals(userY)) {
            throw new IllegalArgumentException("A conversation needs two distinct users");
        }
        // Postgres compares uuid values byte-wise (unsigned), which disagrees with
        // java.util.UUID#compareTo (signed long on the MSBs) whenever the two UUIDs' most
        // significant bytes differ in sign bit — ordering by the canonical string form matches
        // Postgres's ordering exactly, satisfying the `CHECK (user_a_id < user_b_id)` constraint.
        boolean xFirst = userX.toString().compareTo(userY.toString()) < 0;
        return xFirst ? new ConversationParticipants(userX, userY) : new ConversationParticipants(userY, userX);
    }
}
