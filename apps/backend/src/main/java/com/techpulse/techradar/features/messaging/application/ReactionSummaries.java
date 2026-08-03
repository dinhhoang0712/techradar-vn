package com.techpulse.techradar.features.messaging.application;

import com.techpulse.techradar.features.messaging.domain.MessageReactionSummary;
import com.techpulse.techradar.features.messaging.ports.MessageReactionRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Groups raw reaction rows into a per-emoji count, from one viewer's perspective. */
final class ReactionSummaries {

    private ReactionSummaries() {
    }

    static List<MessageReactionSummary> summarize(Collection<MessageReactionRepository.ReactionRow> rows, UUID viewerId) {
        return rows.stream()
                .collect(Collectors.groupingBy(MessageReactionRepository.ReactionRow::emoji))
                .entrySet().stream()
                .map(e -> new MessageReactionSummary(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().anyMatch(r -> r.userId().equals(viewerId))))
                .sorted(Comparator.comparing(MessageReactionSummary::emoji))
                .toList();
    }
}
