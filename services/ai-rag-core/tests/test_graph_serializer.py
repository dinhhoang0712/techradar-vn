from app.core.graph_serializer import triples_to_jsonld


def test_triples_to_jsonld_empty_triples():
    result = triples_to_jsonld([], ["Java"])

    assert result["@graph"] == []
    assert result["seed_entities"] == ["Java"]
    assert "@context" in result


def test_triples_to_jsonld_maps_triple_fields():
    triples = [
        {
            "subject": "Java",
            "subject_type": "Technology",
            "predicate": "RELATED_TO",
            "object": "Kotlin",
            "object_type": "Technology",
            "hop": 1,
        }
    ]

    result = triples_to_jsonld(triples, ["Java"])

    assert len(result["@graph"]) == 1
    node = result["@graph"][0]
    assert node["@type"] == "Technology"
    assert node["name"] == "Java"
    assert node["relation"] == "RELATED_TO"
    assert node["target"] == {"@type": "Technology", "name": "Kotlin"}
    assert node["hop"] == 1


def test_triples_to_jsonld_context_maps_known_predicates():
    result = triples_to_jsonld([], [])
    context = result["@context"]

    assert context["REQUIRES"].endswith("#requires")
    assert context["POSTED_BY"] == context["HIRES_FOR"]  # cả 2 trỏ cùng 1 IRI postedBy
