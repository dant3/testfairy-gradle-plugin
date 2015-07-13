package com.testfairy.plugins.gradle
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

class TestFairyPluginTest {
	@Test void canApplyPlugin() {
		Project project = ProjectBuilder.builder().build()
		def androidExt = project.extensions.create('android', MockAndroidExtension)
		project.apply(plugin: "testfairy")
		project.task("packageDebug", type: MockPackageTask)
		Assert.assertNotNull(project.tasks.findByName("testfairyDebug"))
	}
}
