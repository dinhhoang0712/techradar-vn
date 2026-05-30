import json
import os
import sys
import types
from pathlib import Path
import time
import importlib.util

import pytest
from conftest import FakeElement, install_fake_selenium

@pytest.mark.integration
def test_full_pipeline_flow(tmp_path, monkeypatch):
    """
    End-to-end integration test:
    1. Scrape (DT) -> raw_data_DT_part1.json
    2. Filter -> filtered_data_DT_part1.json
    3. Extract -> extracted_data_phobert_DT_part1.json
    """
    root = Path(__file__).resolve().parents[2]
    
    # --- 1. SCRAPE ---
    scrape_script = root / "src" / "data-pipeline" / "scrape_from_DT.py"
    
    class Driver:
        def __init__(self, exc): self.no_such_exc = exc
        def get(self, url): pass
        def quit(self): pass
        def find_element(self, by, sel):
            if sel == "a[aria-label]": return FakeElement(text="DT")
            if sel == 'a[data-prop="sapo"]': return FakeElement(attrs={"href": "https://dt.vn/1"})
            if sel == "desktop-in-article":
                elem = FakeElement()
                elem._elements["p"] = [FakeElement("AI Engineer Python")]
                return elem
            raise self.no_such_exc(sel)
        def find_elements(self, by, sel):
            if sel == "div.article-content":
                art = FakeElement()
                art._elements['a[data-prop="sapo"]'] = [FakeElement(attrs={"href": "https://dt.vn/1"})]
                art._elements['h3.article-title'] = [FakeElement(text="Tuyển AI Engineer")]
                return [art]
            return []

    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)
    driver = Driver(no_such_exc)
    install_fake_selenium(monkeypatch, driver)
    
    monkeypatch.setattr(time, "sleep", lambda _: None)
    monkeypatch.setattr("builtins.print", lambda *args, **kwargs: None)
    monkeypatch.chdir(tmp_path)
    
    scrape_content = scrape_script.read_text(encoding="utf-8")
    scrape_content = scrape_content.replace("num_pages = 10", "num_pages = 1")
    scrape_content = scrape_content.replace("part = 2", "part = 1")
    
    exec(scrape_content, {
        "__name__": "__main__", "webdriver": sys.modules["selenium.webdriver"],
        "By": sys.modules["selenium.webdriver.common.by"].By,
        "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
        "NoSuchElementException": no_such_exc,
        "json": json, "os": os, "datetime": sys.modules["datetime"].datetime, "time": time
    })
    
    raw_file = tmp_path / "raw_data" / "raw_data_DT_part1.json"
    assert raw_file.exists()
    
    # --- 2. FILTER ---
    torch_mod = types.ModuleType("torch")
    torch_mod.cuda = types.SimpleNamespace(is_available=lambda: False)
    torch_mod.device = lambda n: n
    class NoGrad:
        def __enter__(self): pass
        def __exit__(self, *a): pass
    torch_mod.no_grad = lambda: NoGrad()
    class Scalar:
        def __init__(self, val): self.val = val
        def item(self): return self.val
    class ProbVector(list):
        def __getitem__(self, idx): return Scalar(super().__getitem__(idx))
    class SoftmaxResult(list):
        def __getitem__(self, idx): return ProbVector(super().__getitem__(idx))

    torch_mod.softmax = lambda l, dim: SoftmaxResult([[0.1, 0.9]])
    torch_mod.argmax = lambda p, dim: Scalar(1)
    
    transformers_mod = types.ModuleType("transformers")
    class FakeBatch(dict):
        def to(self, _device): return self
    class FakeTokenizer:
        def __call__(self, *a, **k): return FakeBatch()
        def encode(self, text, **k): return [1, 2, 3]
    transformers_mod.AutoTokenizer = types.SimpleNamespace(from_pretrained=lambda _: FakeTokenizer())
    class FakeModel:
        def eval(self): pass
        def to(self, d): pass
        def __call__(self, **k): return types.SimpleNamespace(logits=[[0.1, 0.9]])
    transformers_mod.AutoModelForSequenceClassification = types.SimpleNamespace(
        from_pretrained=lambda _: FakeModel()
    )
    
    uts_mod = types.ModuleType("underthesea")
    uts_mod.word_tokenize = lambda t, format=None: t
    uts_mod.ner = lambda t: []
    
    monkeypatch.setitem(sys.modules, "torch", torch_mod)
    monkeypatch.setitem(sys.modules, "transformers", transformers_mod)
    monkeypatch.setitem(sys.modules, "underthesea", uts_mod)
    
    filter_script = root / "src" / "data-pipeline" / "filter_data.py"
    spec = importlib.util.spec_from_file_location("filter_module", filter_script)
    filter_mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(filter_mod)
    
    monkeypatch.setattr(filter_mod, "RAW_DATA_DIR", str(tmp_path / "raw_data"))
    monkeypatch.setattr(filter_mod, "FILTERED_DATA_DIR", str(tmp_path / "filtered_data"))
    
    filter_mod.main()
    
    filtered_file = tmp_path / "filtered_data" / "filtered_data_DT_part1.json"
    assert filtered_file.exists()
    
    # --- 3. EXTRACT ---
    extract_script = root / "src" / "data-pipeline" / "extract_data.py"
    spec = importlib.util.spec_from_file_location("extract_module", extract_script)
    extract_mod = importlib.util.module_from_spec(spec)
    
    def mock_pipeline(*args, **kwargs):
        return lambda text: [{"word": "Sam Altman", "entity_group": "PER", "score": 1.0}]
    transformers_mod.pipeline = mock_pipeline
    transformers_mod.AutoModelForTokenClassification = types.SimpleNamespace(from_pretrained=lambda _: None)
    
    spec.loader.exec_module(extract_mod)
    
    monkeypatch.setattr(extract_mod, "FILTERED_DATA_DIR", str(tmp_path / "filtered_data"))
    monkeypatch.setattr(extract_mod, "EXTRACTED_DATA_DIR", str(tmp_path / "extracted_data"))
    
    # Fix argv for main()
    monkeypatch.setattr(sys, "argv", ["extract_data.py", "--dir", str(tmp_path / "filtered_data")])
    
    extract_mod.main()
    
    extracted_file = tmp_path / "extracted_data" / "extracted_data_phobert_DT_part1.json"
    assert extracted_file.exists()
    
    with open(extracted_file, "r", encoding="utf-8") as f:
        final_data = json.load(f)
    
    assert len(final_data["post_detail"]) == 1
    assert final_data["post_detail"][0]["is_relevant"] is True
    assert "Sam Altman" in final_data["post_detail"][0]["entities"]["PER"]
    assert "Python" in final_data["post_detail"][0]["entities"]["TECH"]


@pytest.mark.integration
def test_full_pipeline_array_flow(tmp_path, monkeypatch):
    """
    Test pipeline flow with new array format:
    1. Provide raw_data_Array.json (direct list)
    2. Filter -> filtered_data_Array.json (direct list)
    3. Extract -> extracted_data_phobert_Array.json (direct list)
    """
    root = Path(__file__).resolve().parents[2]
    
    # Setup directories
    raw_dir = tmp_path / "raw_data"
    filtered_dir = tmp_path / "filtered_data"
    extracted_dir = tmp_path / "extracted_data"
    raw_dir.mkdir()
    
    # Create raw array file
    raw_file = raw_dir / "raw_data_Array.json"
    raw_file.write_text(json.dumps([
        {"job_title": "AI Expert", "job_description": "Working with Python", "benefits": "High pay", "requirements": "PhD"}
    ], ensure_ascii=False), encoding="utf-8")
    
    # Setup mocks (same as test_full_pipeline_flow)
    # ... assuming they are already set in sys.modules or we reuse them
    # For this test, we need to ensure the mocks are active
    
    # --- 1. FILTER ---
    filter_script = root / "src" / "data-pipeline" / "filter_data.py"
    
    # Mock PhoBERT loading in filter_data
    transformers_mod = types.ModuleType("transformers")
    transformers_mod.AutoModelForSequenceClassification = types.SimpleNamespace(from_pretrained=lambda _: types.SimpleNamespace(eval=lambda: None, to=lambda _: None))
    transformers_mod.AutoTokenizer = types.SimpleNamespace(from_pretrained=lambda _: lambda *a, **k: types.SimpleNamespace(to=lambda _: {}))
    monkeypatch.setitem(sys.modules, "transformers", transformers_mod)

    # Mock torch and underthesea if needed
    torch_mod = types.ModuleType("torch")
    torch_mod.cuda = types.SimpleNamespace(is_available=lambda: False)
    torch_mod.device = lambda n: n
    class NoGrad:
        def __enter__(self): pass
        def __exit__(self, *a): pass
    torch_mod.no_grad = lambda: NoGrad()
    monkeypatch.setitem(sys.modules, "torch", torch_mod)
    
    uts_mod = types.ModuleType("underthesea")
    uts_mod.word_tokenize = lambda t, format=None: t
    uts_mod.ner = lambda t: []
    monkeypatch.setitem(sys.modules, "underthesea", uts_mod)

    spec = importlib.util.spec_from_file_location("filter_mod_array", filter_script)
    filter_mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(filter_mod)
    
    monkeypatch.setattr(filter_mod, "RAW_DATA_DIR", str(raw_dir))
    monkeypatch.setattr(filter_mod, "FILTERED_DATA_DIR", str(filtered_dir))
    
    # Mock predict_one for filter
    monkeypatch.setattr(filter_mod, "predict_one", lambda _: (True, 0.99))
    
    filter_mod.main()
    
    filtered_file = filtered_dir / "filtered_data_Array.json"
    assert filtered_file.exists()
    filtered_data = json.loads(filtered_file.read_text(encoding="utf-8"))
    assert isinstance(filtered_data, list)
    assert filtered_data[0]["is_relevant"] is True
    
    # --- 2. EXTRACT ---
    extract_script = root / "src" / "data-pipeline" / "extract_data.py"
    
    # Mock NER loading in extract_data
    transformers_mod.pipeline = lambda *a, **k: lambda text: []
    transformers_mod.AutoModelForTokenClassification = types.SimpleNamespace(from_pretrained=lambda _: None)

    spec = importlib.util.spec_from_file_location("extract_mod_array", extract_script)
    extract_mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(extract_mod)
    
    # Mock NER for extract
    def mock_extract_ner(text):
        return [{"entity": "Python", "label": "TECH", "score": 1.0}]
    
    monkeypatch.setattr(extract_mod, "extract_entities_ner", mock_extract_ner)
    monkeypatch.setattr(extract_mod, "FILTERED_DATA_DIR", str(filtered_dir))
    monkeypatch.setattr(extract_mod, "EXTRACTED_DATA_DIR", str(extracted_dir))
    monkeypatch.setattr(sys, "argv", ["extract_data.py", "--dir", str(filtered_dir)])
    
    extract_mod.main()
    
    extracted_file = extracted_dir / "extracted_data_phobert_Array.json"
    assert extracted_file.exists()
    extracted_data = json.loads(extracted_file.read_text(encoding="utf-8"))
    assert isinstance(extracted_data, list)
    assert "Python" in extracted_data[0]["entities"]["TECH"]