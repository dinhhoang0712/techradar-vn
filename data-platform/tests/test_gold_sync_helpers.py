from __future__ import annotations

import gold.neo4j_article_sync as article_sync
import gold.neo4j_job_sync as job_sync


def test_slugify_lowercases_and_replaces_non_alphanumeric():
    assert article_sync._slugify("Senior Python Developer!") == "senior-python-developer"
    assert job_sync._slugify("Senior Python Developer!") == "senior-python-developer"


def test_slugify_strips_leading_and_trailing_dashes():
    assert article_sync._slugify("  #FPT Software  ") == "fpt-software"


def test_slugify_handles_none_and_empty():
    assert article_sync._slugify(None) == ""
    assert article_sync._slugify("") == ""


def test_job_id_is_stable_md5_of_source_url():
    import hashlib

    url = "https://example.com/job-1"
    assert job_sync._job_id(url) == hashlib.md5(url.encode("utf-8")).hexdigest()


def test_job_id_handles_none_source_url():
    import hashlib

    assert job_sync._job_id(None) == hashlib.md5(b"").hexdigest()


def test_chunks_splits_into_fixed_size_batches():
    items = list(range(7))
    assert list(article_sync._chunks(items, 3)) == [[0, 1, 2], [3, 4, 5], [6]]


def test_chunks_returns_nothing_for_empty_input():
    assert list(article_sync._chunks([], 3)) == []
