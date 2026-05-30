from __future__ import annotations

def test_get_statistics_returns_all_node_and_relationship_counts(importer_factory, fake_session_class):
    session = fake_session_class(
        results=[
            {"count": 2},
            {"count": 3},
            {"count": 4},
            {"count": 5},
            {"count": 6},
            {"count": 7},
            {"count": 12},
        ]
    )
    importer, driver, session, captured = importer_factory(session=session)

    assert importer.connect() is True

    stats = importer.get_statistics()

    assert stats == {
        "Article": 2,
        "Technology": 3,
        "Company": 4,
        "Skill": 5,
        "Person": 6,
        "Job": 7,
        "Relationships": 12,
    }
    assert len(session.run_calls) == 7
