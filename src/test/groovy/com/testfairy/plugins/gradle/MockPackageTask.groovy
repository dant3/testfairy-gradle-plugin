package com.testfairy.plugins.gradle

import org.gradle.api.DefaultTask

class MockPackageTask extends DefaultTask {
	public def outputFile = getTemporaryDir().createNewFile()
}
