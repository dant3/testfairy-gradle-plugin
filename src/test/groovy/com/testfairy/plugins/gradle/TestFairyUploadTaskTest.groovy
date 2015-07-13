package com.testfairy.plugins.gradle
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

class TestFairyUploadTaskTest {
    @Test void canAddTaskToProject() {
        Project project = ProjectBuilder.builder().build()
        def task = project.task('uploadTask', type: TestFairyUploadTask)
        Assert.assertTrue(task instanceof TestFairyUploadTask)
    }
}
