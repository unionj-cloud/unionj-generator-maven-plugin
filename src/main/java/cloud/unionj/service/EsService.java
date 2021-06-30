package cloud.unionj.service;

import cloud.unionj.model.Doc;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.DocumentResult;
import io.searchbox.core.Index;
import lombok.Data;
import lombok.SneakyThrows;

/**
 * @author created by wubin
 * @version v0.0.1
 * description: cloud.unionj.service
 * date:2021/6/30
 */
@Data
public class EsService {

  private JestClient client;
  private String esIndex;
  private String esType;
  private String esAddr;

  public EsService(String esAddr, String esIndex, String esType) {
    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(new HttpClientConfig
        .Builder(esAddr)
        .multiThreaded(true)
        .build());
    JestClient client = factory.getObject();
    this.client = client;
    this.esIndex = esIndex;
    this.esType = esType;
    this.esAddr = esAddr;
  }

  @SneakyThrows
  public String indexDoc(Doc doc) {
    Index index = new Index.Builder(doc).index(esIndex).type(esType).build();
    DocumentResult result = client.execute(index);
    return result.getId();
  }
}
