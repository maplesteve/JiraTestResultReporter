from selenium import selenium
from SeleniumTest import SeleniumTest

class testPluginLinks(SeleniumTest):

    def test_daa_PluginLinks(self):
        sel = self.selenium
        # open plugin manager page
        sel.open("/pluginManager/installed")
        sel.click("link=Jenkins JiraTestResultReporter plugin")
        sel.wait_for_page_to_load("30000")
        self.assertEqual("JiraTestResultReporter-plugin - Jenkins - Jenkins Wiki", sel.get_title())


