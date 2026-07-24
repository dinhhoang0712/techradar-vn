from app.core.strategy_selector import select_strategy


def _extracted(technologies=None, companies=None, job_titles=None, locations=None):
    return {
        "technologies": technologies or [],
        "companies": companies or [],
        "job_titles": job_titles or [],
        "locations": locations or [],
    }


def test_no_entities_disables_graph_expansion_and_sql():
    strategy = select_strategy("Hôm nay thời tiết thế nào?", _extracted())

    assert strategy.use_vector is True
    assert strategy.use_graph is False
    assert strategy.graph_expansion_depth == 0
    assert strategy.use_sql_analytics is False


def test_tech_entity_enables_graph_with_shallow_expansion():
    strategy = select_strategy("React là gì?", _extracted(technologies=["React"]))

    assert strategy.use_graph is True
    assert strategy.graph_expansion_depth == 1
    assert strategy.tech_names == ["React"]


def test_multihop_intent_triggers_deep_expansion():
    strategy = select_strategy(
        "So sánh Java và Python, nên học cái nào trước?",
        _extracted(technologies=["Java", "Python"]),
    )

    assert strategy.graph_expansion_depth == 2


def test_analytics_intent_with_tech_enables_sql():
    strategy = select_strategy("Xu hướng lương của Java thế nào?", _extracted(technologies=["Java"]))

    assert strategy.use_sql_analytics is True


def test_analytics_intent_without_tech_does_not_enable_sql():
    strategy = select_strategy("Xu hướng ngành công nghệ thông tin ra sao?", _extracted())

    assert strategy.use_sql_analytics is False
    assert strategy.use_graph is False


def test_company_only_query_enables_graph_without_expansion_depth_2():
    strategy = select_strategy("Tiki đang tuyển những vị trí nào?", _extracted(companies=["Tiki"]))

    assert strategy.use_graph is True
    assert strategy.graph_expansion_depth == 1
    assert strategy.company_names == ["Tiki"]


def test_matched_signals_reflects_extracted_entities():
    strategy = select_strategy(
        "FPT tuyển Python Developer ở Hà Nội không?",
        _extracted(technologies=["Python"], companies=["FPT"], job_titles=["Developer"], locations=["Hà Nội"]),
    )

    assert strategy.matched_signals == {
        "technologies": ["Python"],
        "companies": ["FPT"],
        "job_titles": ["Developer"],
        "locations": ["Hà Nội"],
    }
