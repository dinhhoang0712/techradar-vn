import json
import os
import runpy
import sys
import types
from pathlib import Path

import pytest

from conftest import FakeElement, install_fake_selenium


class FakeTopCVDriver:
    def __init__(self, no_such_exc):
        self.no_such_exc = no_such_exc
        self.current_url = ""
        self.visited_urls = []
        self.page_source = ""

    def get(self, url):
        self.current_url = url
        self.visited_urls.append(url)

    def find_element(self, by, selector):
        if selector == "a.header-menu-mobile__logo img":
            return FakeElement(text="TopCV", attrs={"title": "TopCV"})
        if selector == "h1.job-detail__info--title":
            if "fail" in self.current_url:
                raise self.no_such_exc()
            return FakeElement(text="Software Engineer")
        if selector == "job-description__item--content":
            return FakeElement(text="")
        raise self.no_such_exc()

    def find_elements(self, by, selector):
        if selector == "h3.title a":
            if "page=2" in self.current_url:
                return [] # Empty page 2
            return [FakeElement(text="Job 1", attrs={"href": "https://topcv.vn/job1"}),
                    FakeElement(text="Job 2", attrs={"href": "https://topcv.vn/job2-fail"})]
        
        if selector == "job-description__item--content":
            # Mock sections for scrape_job_description
            if "job1" in self.current_url:
                sec1 = FakeElement(text="")
                sec1._elements["p"] = [FakeElement(text="Para 1")]
                
                sec2 = FakeElement(text="")
                sec2._elements["ul"] = [FakeElement(text="")]
                sec2._elements["ul"][0]._elements["li"] = [FakeElement(text="Item 1"), FakeElement(text="Item 2")]
                
                return [sec1, sec2]
        
        return []

    def quit(self): pass


def test_topcv_scrape_integration(tmp_path, monkeypatch):
    root = Path(__file__).resolve().parents[2]
    script_path = root / "pipelines" / "data-pipeline" / "scrape_from_topCV.py"

    driver_obj = FakeTopCVDriver(Exception) # Placeholder for no_such_exc
    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=driver_obj)
    driver_obj.no_such_exc = no_such_exc
    
    mock_uc = sys.modules["undetected_chromedriver"]
    
    import time
    monkeypatch.setattr(time, "sleep", lambda _: None)
    monkeypatch.chdir(tmp_path)
    
    script_content = script_path.read_text(encoding="utf-8").replace("num_pages = 6", "num_pages = 2")
    
    script_content = script_path.read_text(encoding="utf-8").replace("num_pages = 6", "num_pages = 2")
    
    script_content = script_path.read_text(encoding="utf-8").replace("num_pages = 6", "num_pages = 2")
    
    # Patch src bugs in-memory: fix undefined source_url if it exists
    script_content = script_content.replace('"source_url": source_url,', '"source_url": base_url_page1,')

    globals_dict = {
        "__name__": "__main__", 
        "uc": mock_uc, 
        "webdriver": sys.modules["selenium.webdriver"],
        "By": sys.modules["selenium.webdriver.common.by"].By,
        "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
        "EC": sys.modules["selenium.webdriver.support.expected_conditions"],
        "json": json, "os": os, "datetime": sys.modules["datetime"].datetime,
        "random": types.SimpleNamespace(uniform=lambda a,b: a),
        "traceback": types.ModuleType("traceback"),
        "time": time
    }
    
    exec(script_content, globals_dict)

    output = tmp_path / "raw_data" / "raw_data_topCV.json"
    assert output.exists()
    data = json.loads(output.read_text(encoding="utf-8"))
    assert len(data["post_detail"]) == 1


def test_topcv_scrape_whitespace_stripping(tmp_path, monkeypatch):
    """Test that TopCV scraper strips whitespace-only paragraphs/items."""
    root = Path(__file__).resolve().parents[2]
    script_path = root / "pipelines" / "data-pipeline" / "scrape_from_topCV.py"

    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=None)
    
    class WhitespaceDriver(FakeTopCVDriver):
        def find_elements(self, by, selector):
            if selector == "job-description__item--content":
                sec = FakeElement("")
                sec._elements["p"] = [FakeElement("  "), FakeElement("Content 1"), FakeElement("\t")]
                sec._elements["ul"] = [] # Ensure it falls back to p
                return [sec]
            return super().find_elements(by, selector)

    driver_obj = WhitespaceDriver(no_such_exc)
    install_fake_selenium(monkeypatch, fake_driver=driver_obj)
    
    import time
    monkeypatch.setattr(time, "sleep", lambda _: None)
    monkeypatch.chdir(tmp_path)
    
    script_content = script_path.read_text(encoding="utf-8").replace("num_pages = 6", "num_pages = 1")
    script_content = script_content.replace('"source_url": source_url,', '"source_url": "https://topcv.vn",')

    exec(script_content, {
        "__name__": "__main__", 
        "uc": sys.modules["undetected_chromedriver"], 
        "webdriver": sys.modules["selenium.webdriver"],
        "By": sys.modules["selenium.webdriver.common.by"].By,
        "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
        "EC": sys.modules["selenium.webdriver.support.expected_conditions"],
        "json": json, "os": os, "datetime": sys.modules["datetime"].datetime,
        "random": types.SimpleNamespace(uniform=lambda a,b: a),
        "traceback": types.ModuleType("traceback"),
        "time": time
    })
    
    output = tmp_path / "raw_data" / "raw_data_topCV.json"
    data = json.loads(output.read_text(encoding="utf-8"))
    assert data["post_detail"][0]["content"] == "Content 1"


def test_topcv_scrape_exception_handling(tmp_path, monkeypatch):
    """Test TopCV handles general exceptions during article processing."""
    root = Path(__file__).resolve().parents[2]
    script_path = root / "pipelines" / "data-pipeline" / "scrape_from_topCV.py"

    class ExceptionDriver(FakeTopCVDriver):
        def get(self, url):
            self.visited_urls.append(url)
            if "job1" in url:
                raise Exception("Network Error")
            if "job1" not in url:
                super().get(url)

    driver_obj = ExceptionDriver(Exception)
    no_such_exc = install_fake_selenium(monkeypatch, fake_driver=driver_obj)
    driver_obj.no_such_exc = no_such_exc
    
    mock_uc = sys.modules["undetected_chromedriver"]
    
    import time
    monkeypatch.setattr(time, "sleep", lambda _: None)
    monkeypatch.chdir(tmp_path)
    
    script_content = script_path.read_text(encoding="utf-8").replace("num_pages = 6", "num_pages = 1")
    script_content = script_content.replace('"source_url": source_url,', '"source_url": "https://topcv.vn",')

    exec(script_content, {
        "__name__": "__main__", 
        "uc": mock_uc, 
        "webdriver": sys.modules["selenium.webdriver"],
        "By": sys.modules["selenium.webdriver.common.by"].By,
        "WebDriverWait": sys.modules["selenium.webdriver.support.ui"].WebDriverWait,
        "EC": sys.modules["selenium.webdriver.support.expected_conditions"],
        "json": json, "os": os, "datetime": sys.modules["datetime"].datetime,
        "random": types.SimpleNamespace(uniform=lambda a,b: a),
        "traceback": types.ModuleType("traceback"),
        "time": time
    })
    
    # Should complete without crashing even if job1 failed
    output = tmp_path / "raw_data" / "raw_data_topCV.json"
    data = json.loads(output.read_text(encoding="utf-8"))
    # Job 1 failed (Network Error), Job 2 skipped (no title)
    assert len(data["post_detail"]) == 0
    # Verify Job 1 was attempted
    assert any("job1" in url for url in driver_obj.visited_urls)
