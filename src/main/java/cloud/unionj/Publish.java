package cloud.unionj;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import cloud.unionj.generator.openapi3.model.Openapi3;
import cloud.unionj.model.Doc;
import cloud.unionj.service.EsService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

@Mojo(name = "publish", defaultPhase = LifecyclePhase.DEPLOY, requiresDependencyResolution = ResolutionScope.COMPILE)
public class Publish extends AbstractMojo {

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  MavenProject project;

  @Parameter(property = "entry")
  String entry;

  @Parameter(property = "esAddr")
  String esAddr;

  @Parameter(property = "esIndex")
  String esIndex;

  @Parameter(property = "esType")
  String esType;

  @Component
  private MavenProject mavenProject;

  @Component
  private MavenSession mavenSession;

  @Component
  private BuildPluginManager pluginManager;

  private ClassLoader getClassLoader(MavenProject project) {
    try {
      List classpathElements = project.getCompileClasspathElements();
      classpathElements.add(project.getBuild().getOutputDirectory());
      classpathElements.add(project.getBuild().getTestOutputDirectory());
      URL urls[] = new URL[classpathElements.size()];
      for (int i = 0; i < classpathElements.size(); ++i) {
        urls[i] = new File((String) classpathElements.get(i)).toURL();
      }
      return new URLClassLoader(urls, this.getClass().getClassLoader());
    } catch (Exception e) {
      return this.getClass().getClassLoader();
    }
  }

  @SneakyThrows
  public void execute() {
    System.out.println(project.getArtifactId());
    System.out.println(project.getBasedir());
    System.out.println(project.getModules());
    for (Object module : project.getModules()) {
      System.out.println(module);
    }
    System.out.println(esAddr);
    System.out.println(esIndex);
    System.out.println(esType);

    Doc doc = new Doc();
    executeMojo(
        plugin(
            groupId("pl.project13.maven"),
            artifactId("git-commit-id-plugin"),
            version("4.0.0")
        ),
        goal("revision"),
        configuration(
            element(name("generateGitPropertiesFile"), "false"),
            element(name("injectIntoSysProperties"), "true")
        ),
        executionEnvironment(
            mavenProject,
            mavenSession,
            pluginManager
        )
    );
    Properties properties = System.getProperties();
    getLog().info("~~~~~~~~~~~~~~~~~~~GIT PROPERTIES~~~~~~~~~~~~~~~~~~~");
    Doc.Git git = new Doc.Git();
    properties.forEach((k, v) -> {
      if (k.toString().startsWith("git")) {
        getLog().info(k + "=" + v);
        switch (k.toString()) {
          case "git.branch": {
            git.setBranch((String) v);
            break;
          }
          case "git.closest.tag.name": {
            git.setClosestTag((String) v);
            break;
          }
          case "git.commit.time": {
            // 2021-06-29T13:11:25+0800
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ");
            String commitAt = dateTimeFormatter.parseDateTime((String) v).withZone(DateTimeZone.UTC).toString();
            git.setCommitAt(commitAt);
            break;
          }
          case "git.commit.id": {
            git.setCommitId((String) v);
            break;
          }
          case "git.commit.user.name": {
            git.setCommitUser((String) v);
            break;
          }
          case "git.commit.id.abbrev": {
            git.setCommitIdAbbr((String) v);
            break;
          }
          case "git.commit.message.full": {
            git.setFullMessage((String) v);
            break;
          }
        }
      }
    });
    getLog().info("~~~~~~~~~~~~~~~~~~~GIT PROPERTIES~~~~~~~~~~~~~~~~~~~");

    doc.setGit(git);
    doc.setCreateAt(DateTime.now().withZone(DateTimeZone.UTC).toString());

    String designClass = "gen.Openapi3Designer";
    String designMethod = "design";
    if (this.entry != null && this.entry.length() > 0) {
      designClass = StringUtils.substring(this.entry, 0, StringUtils.lastIndexOf(this.entry, "."));
      designMethod = StringUtils.substring(this.entry, StringUtils.lastIndexOf(this.entry, ".") + 1);
    }
    try {
      Class<?> designer = this.getClassLoader(project).loadClass(designClass);
      Method design = designer.getMethod(designMethod);
      Openapi3 openAPI = (Openapi3) design.invoke(null);
      if (openAPI.getInfo() != null) {
        doc.setService(openAPI.getInfo().getTitle());
        doc.setVersion(openAPI.getInfo().getVersion());
      }
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
      doc.setApi(objectMapper.writeValueAsString(openAPI));
      EsService esService = new EsService(esAddr, esIndex, esType);
      String id = esService.indexDoc(doc);
      System.out.println("Document published to " + esAddr + ". Id is " + id);
    } catch (Exception e) {
      e.printStackTrace();
    }
    getLog().info("Document published");
  }
}
