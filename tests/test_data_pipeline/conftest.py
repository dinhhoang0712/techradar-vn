import sys
import types


class FakeElement:
    def __init__(self, text="", attrs=None):
        self.text = text
        self._attrs = attrs or {}
        self._elements = {}

    def get_attribute(self, key):
        return self._attrs.get(key, "")

    def is_displayed(self):
        return True

    def find_elements(self, by, selector):
        return self._elements.get(selector, [])

    def find_element(self, by, selector):
        elements = self.find_elements(by, selector)
        if elements:
            return elements[0]
        raise NoSuchElementException(f"Element not found: {selector}")


class DummyWait:
    def __init__(self, *args, **kwargs):
        pass


class DummySelect:
    def __init__(self, *args, **kwargs):
        pass


class NoSuchElementException(Exception):
    pass


class StaleElementReferenceException(Exception):
    pass


def install_fake_selenium(monkeypatch, fake_driver):
    selenium_mod = types.ModuleType("selenium")

    webdriver_mod = types.ModuleType("selenium.webdriver")
    webdriver_mod.Chrome = lambda *args, **kwargs: fake_driver

    chrome_mod = types.ModuleType("selenium.webdriver.chrome")
    chrome_options_mod = types.ModuleType("selenium.webdriver.chrome.options")

    class Options:
        def __init__(self):
            self.args = []

        def add_argument(self, arg):
            self.args.append(arg)

    chrome_options_mod.Options = Options

    by_mod = types.ModuleType("selenium.webdriver.common.by")
    by_mod.By = types.SimpleNamespace(
        CSS_SELECTOR="css selector", 
        ID="id",
        CLASS_NAME="class name",
        TAG_NAME="tag name"
    )

    ui_mod = types.ModuleType("selenium.webdriver.support.ui")
    ui_mod.WebDriverWait = DummyWait
    ui_mod.Select = DummySelect

    support_mod = types.ModuleType("selenium.webdriver.support")
    ec_mod = types.ModuleType("selenium.webdriver.support.expected_conditions")

    common_exc_mod = types.ModuleType("selenium.common.exceptions")
    common_exc_mod.NoSuchElementException = NoSuchElementException
    common_exc_mod.StaleElementReferenceException = StaleElementReferenceException

    uc_mod = types.ModuleType("undetected_chromedriver")
    uc_mod.Chrome = lambda *args, **kwargs: fake_driver

    monkeypatch.setitem(sys.modules, "selenium", selenium_mod)
    monkeypatch.setitem(sys.modules, "selenium.webdriver", webdriver_mod)
    monkeypatch.setitem(sys.modules, "selenium.webdriver.chrome", chrome_mod)
    monkeypatch.setitem(sys.modules, "selenium.webdriver.chrome.options", chrome_options_mod)
    monkeypatch.setitem(sys.modules, "selenium.webdriver.common.by", by_mod)
    monkeypatch.setitem(sys.modules, "selenium.webdriver.support.ui", ui_mod)
    monkeypatch.setitem(sys.modules, "selenium.webdriver.support", support_mod)
    monkeypatch.setitem(sys.modules, "selenium.webdriver.support.expected_conditions", ec_mod)
    monkeypatch.setitem(sys.modules, "selenium.common.exceptions", common_exc_mod)
    monkeypatch.setitem(sys.modules, "undetected_chromedriver", uc_mod)

    return NoSuchElementException
