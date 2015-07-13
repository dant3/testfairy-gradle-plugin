package com.testfairy.plugins.gradle

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

class TestFairyPlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {
		// create an extension where the apiKey and such settings reside
		def extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)

		project.configure(project) {
			if (it.hasProperty("android")) {
				tasks.whenTaskAdded { projectTask ->
					project.("android").applicationVariants.all { buildVariant ->
						// locate packageRelease and packageDebug tasks
						def expectingTask = "package${buildVariant.name.capitalize()}".toString()
						if (expectingTask.equals(projectTask.name)) {
							def variantName = buildVariant.name.capitalize()

							def configureTask = configureUploadTask(variantName, buildVariant, projectTask)
							// create new task with name such as testfairyRelease and testfairyDebug
							configureTask.execute(
									project.tasks.create("testfairy${variantName}", TestFairyAndroidBuildUploadTask)
							)
						}
					}
				}
			}
			// TODO: iOS app uploading definition
		}
	}

	private static Action<TestFairyAndroidBuildUploadTask> configureUploadTask(variantName, androidAppVariant, packageTask) {
		return new Action<TestFairyAndroidBuildUploadTask>() {
			@Override
			void execute(TestFairyAndroidBuildUploadTask task) {
				task.description = "Uploads the ${variantName} build to TestFairy"
				task.variant = androidAppVariant
				// use outputFile from packageApp task
				task.uploadedFile = packageTask.outputFile
				task.dependsOn(packageTask)
			}
		}
	}
}

