#!/usr/bin/python

import sys, unittest, xmlrunner
from SeleniumTest import SeleniumTest
import testLogin, testConfigSyntaxChecks, testConfigChanges, testPluginLinks

if __name__ == '__main__':
    suite = unittest.TestLoader().loadTestsFromModule(testLogin)
    suite.addTests(unittest.TestLoader().loadTestsFromModule(testConfigSyntaxChecks))
    suite.addTests(unittest.TestLoader().loadTestsFromModule(testConfigChanges))
    suite.addTests(unittest.TestLoader().loadTestsFromModule(testPluginLinks))

#     unittest.TextTestRunner( verbosity=2 ).run(suite)
    xmlrunner.XMLTestRunner(output='test-reports').run(suite)
