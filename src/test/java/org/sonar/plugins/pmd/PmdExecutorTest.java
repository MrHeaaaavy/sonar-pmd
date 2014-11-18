/*
 * SonarQube PMD Plugin
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.pmd;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.InputFile;
import org.sonar.api.resources.Java;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.ProjectFileSystem;
import org.sonar.plugins.java.api.JavaResourceLocator;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PmdExecutorTest {
  PmdExecutor pmdExecutor;

  Project project = mock(Project.class);
  ProjectFileSystem projectFileSystem = mock(ProjectFileSystem.class);
  RulesProfile rulesProfile = mock(RulesProfile.class);
  PmdProfileExporter pmdProfileExporter = mock(PmdProfileExporter.class);
  PmdConfiguration pmdConfiguration = mock(PmdConfiguration.class);
  PmdTemplate pmdTemplate = mock(PmdTemplate.class);
  JavaResourceLocator javaResourceLocator = mock(JavaResourceLocator.class);
  PmdExecutor realPmdExecutor = new PmdExecutor(project, projectFileSystem, rulesProfile, pmdProfileExporter, pmdConfiguration, javaResourceLocator);

  @Before
  public void setUp() {
    pmdExecutor = Mockito.spy(realPmdExecutor);
    doReturn(pmdTemplate).when(pmdExecutor).createPmdTemplate();
    when(projectFileSystem.getSourceCharset()).thenReturn(Charsets.UTF_8);
  }

  @Test
  public void should_execute_pmd_on_source_files_and_test_files() throws Exception {
    InputFile srcFile = file("src/Class.java");
    InputFile tstFile = file("test/ClassTest.java");
    setupPmdRuleSet(PmdConstants.REPOSITORY_KEY, "simple.xml");
    setupPmdRuleSet(PmdConstants.TEST_REPOSITORY_KEY, "junit.xml");
    when(projectFileSystem.getSourceCharset()).thenReturn(Charsets.UTF_8);
    when(projectFileSystem.mainFiles(Java.KEY)).thenReturn(Arrays.asList(srcFile));
    when(projectFileSystem.testFiles(Java.KEY)).thenReturn(Arrays.asList(tstFile));

    Report report = pmdExecutor.execute();

    verify(pmdTemplate).process(eq(srcFile), any(RuleSets.class), any(RuleContext.class));
    verify(pmdTemplate).process(eq(tstFile), any(RuleSets.class), any(RuleContext.class));
    assertThat(report).isNotNull();
  }

  @Test
  public void should_dump_configuration_as_xml() {
    when(pmdProfileExporter.exportProfile(PmdConstants.REPOSITORY_KEY, rulesProfile)).thenReturn(PmdTestUtils.getResourceContent("/org/sonar/plugins/pmd/simple.xml"));
    when(pmdProfileExporter.exportProfile(PmdConstants.TEST_REPOSITORY_KEY, rulesProfile)).thenReturn(PmdTestUtils.getResourceContent("/org/sonar/plugins/pmd/junit.xml"));

    Report report = pmdExecutor.execute();

    verify(pmdConfiguration).dumpXmlReport(report);
  }

  @Test
  public void should_dump_ruleset_as_xml() throws Exception {
    InputFile srcFile = file("src/Class.java");
    InputFile tstFile = file("test/ClassTest.java");
    setupPmdRuleSet(PmdConstants.REPOSITORY_KEY, "simple.xml");
    setupPmdRuleSet(PmdConstants.TEST_REPOSITORY_KEY, "junit.xml");
    when(projectFileSystem.mainFiles(Java.KEY)).thenReturn(Arrays.asList(srcFile));
    when(projectFileSystem.testFiles(Java.KEY)).thenReturn(Arrays.asList(tstFile));

    pmdExecutor.execute();

    verify(pmdConfiguration).dumpXmlRuleSet(PmdConstants.REPOSITORY_KEY, PmdTestUtils.getResourceContent("/org/sonar/plugins/pmd/simple.xml"));
    verify(pmdConfiguration).dumpXmlRuleSet(PmdConstants.TEST_REPOSITORY_KEY, PmdTestUtils.getResourceContent("/org/sonar/plugins/pmd/junit.xml"));
  }

  @Test
  public void should_ignore_empty_test_dir() throws Exception {
    InputFile srcFile = file("src/Class.java");
    doReturn(pmdTemplate).when(pmdExecutor).createPmdTemplate();
    setupPmdRuleSet(PmdConstants.REPOSITORY_KEY, "simple.xml");
    when(projectFileSystem.getSourceCharset()).thenReturn(Charsets.UTF_8);
    when(projectFileSystem.mainFiles(Java.KEY)).thenReturn(Arrays.asList(srcFile));
    when(projectFileSystem.testFiles(Java.KEY)).thenReturn(Collections.<InputFile>emptyList());

    pmdExecutor.execute();

    verify(pmdTemplate).process(eq(srcFile), any(RuleSets.class), any(RuleContext.class));
    verifyNoMoreInteractions(pmdTemplate);
  }

  @Test
  public void should_build_project_classloader_from_javaresourcelocator() throws Exception {
    File file = new File("x");
    when(javaResourceLocator.classpath()).thenReturn(ImmutableList.of(file));
    PmdTemplate template = realPmdExecutor.createPmdTemplate();
    ClassLoader classLoader = template.configuration().getClassLoader();
    assertThat(classLoader).isInstanceOf(URLClassLoader.class);
    URL[] urls = ((URLClassLoader) classLoader).getURLs();
    assertThat(urls).containsOnly(file.toURI().toURL());
  }

  @Test
  public void invalid_classpath_element() throws Exception {
    File invalidFile = mock(File.class);
    when(invalidFile.toURI()).thenReturn(URI.create("x://xxx"));
    when(javaResourceLocator.classpath()).thenReturn(ImmutableList.of(invalidFile));
    try {
      realPmdExecutor.createPmdTemplate();
      Assert.fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).containsIgnoringCase("classpath");
    }
  }

  static InputFile file(String path) {
    InputFile inputFile = mock(InputFile.class);
    when(inputFile.getFile()).thenReturn(new File(path));
    return inputFile;
  }
  
  private void setupPmdRuleSet(String repositoryKey, String profileFileName) throws IOException {
    File ruleSetDirectory = new File("src/test/resources/org/sonar/plugins/pmd/");
    File file = new File(ruleSetDirectory, profileFileName);
    String profileContent = Files.toString(file, Charsets.UTF_8);
    when(pmdProfileExporter.exportProfile(repositoryKey, rulesProfile)).thenReturn(profileContent);
    when(pmdConfiguration.dumpXmlRuleSet(repositoryKey, profileContent)).thenReturn(file);
  }
}
