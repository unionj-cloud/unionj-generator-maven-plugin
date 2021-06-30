package cloud.unionj.model;

import lombok.Data;

/**
 * @author created by wubin
 * @version v0.0.1
 * description: cloud.unionj.model
 * date:2021/6/30
 */
@Data
public class Doc {
  private String api;
  private String createAt;
  private String service;
  private String version;
  private Git git;

  @Data
  public static class Git {
    private String commitId;
    private String commitIdAbbr;
    private String branch;
    private String commitAt;
    private String commitUser;
    private String closestTag;
    private String fullMessage;
  }
}
