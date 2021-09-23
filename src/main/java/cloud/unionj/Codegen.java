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

import cloud.unionj.generator.backend.docparser.BackendDocParser;
import cloud.unionj.generator.backend.docparser.entity.Backend;
import cloud.unionj.generator.backend.springboot.OutputConfig;
import cloud.unionj.generator.backend.springboot.SpringbootFolderGenerator;
import cloud.unionj.generator.openapi3.model.Openapi3;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Mojo(name = "codegen", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class Codegen extends AbstractMojo {

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  MavenProject project;

  @Parameter(property = "entry")
  String entry;

  @Parameter(property = "protoPkg")
  String protoPkg;

  @Parameter(property = "protoDir")
  String protoDir;

  @Parameter(property = "voPkg")
  String voPkg;

  @Parameter(property = "voDir")
  String voDir;

  @Parameter(property = "controllerPkg")
  String controllerPkg;

  @Parameter(property = "controllerDir")
  String controllerDir;

  @Parameter(property = "servicePkg")
  String servicePkg;

  @Parameter(property = "serviceDir")
  String serviceDir;

  @Parameter(property = "parentGroupId")
  String parentGroupId;

  @Parameter(property = "parentArtifactId")
  String parentArtifactId;

  @Parameter(property = "parentVersion")
  String parentVersion;

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

  public void execute() throws MojoExecutionException, MojoFailureException {
    System.out.println(project.getArtifactId());
    System.out.println(project.getBasedir());
    System.out.println(project.getModules());
    for (Object module : project.getModules()) {
      System.out.println(module);
    }
    String[] split = this.protoDir.split("/");
    String protoArtifactId = split[split.length - 1];
    split = this.voDir.split("/");
    String voArtifactId = split[split.length - 1];
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
      Backend backend = BackendDocParser.parse(openAPI);
      SpringbootFolderGenerator springbootFolderGenerator = new SpringbootFolderGenerator.Builder(backend)
          .protoOutput(new OutputConfig(protoPkg, this.protoDir))
          .voOutput(new OutputConfig(voPkg, this.voDir))
          .controllerOutput(new OutputConfig(controllerPkg, this.controllerDir))
          .serviceOutput(new OutputConfig(servicePkg, this.serviceDir))
          .pomProject(true)
          .pomParentGroupId(parentGroupId)
          .pomParentArtifactId(parentArtifactId)
          .pomParentVersion(parentVersion)
          .pomProtoArtifactId(protoArtifactId)
          .pomVoArtifactId(voArtifactId)
          .build();
      springbootFolderGenerator.generate();

      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
      objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
      File oas3SpecFile = new File("openapi3.json");
      FileUtils.writeStringToFile(oas3SpecFile, objectMapper.writeValueAsString(openAPI), StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      e.printStackTrace();
    }
    getLog().info("Code generated");
  }
}
