from app.core.entity_extractor import has_analytics_intent, has_multihop_intent


def test_has_analytics_intent_true_cases():
    assert has_analytics_intent("Xu hướng lương ngành IT thế nào?")
    assert has_analytics_intent("Thống kê số lượng job Python trong năm nay")
    assert has_analytics_intent("Nhu cầu tuyển dụng DevOps đang ra sao?")


def test_has_analytics_intent_false_case():
    assert not has_analytics_intent("React là gì?")
    assert not has_analytics_intent("FPT đang tuyển vị trí gì?")


def test_has_multihop_intent_true_cases():
    assert has_multihop_intent("So sánh Java và Python")
    assert has_multihop_intent("Docker nên học cùng công nghệ nào?")
    assert has_multihop_intent("Muốn xây hệ sinh thái microservices cần gì?")
    assert has_multihop_intent("Vừa cần React vừa cần Node.js thì học gì?")


def test_has_multihop_intent_false_case():
    assert not has_multihop_intent("Docker command cơ bản")
    assert not has_multihop_intent("React là gì?")
