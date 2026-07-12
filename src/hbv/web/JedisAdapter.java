package hbv.web;

import java.time.Duration;
import redis.clients.jedis.*;

public class JedisAdapter {

  // volatile stellt sicher, dass Schreiboperationen sofort für alle Threads sichtbar sind
  private static volatile RedisClient redisClient = null;

  // Privater Konstruktor verhindert Instanziierung von außen
  private JedisAdapter() {}

  public static void init(String host, int port, String password, int maxConnections) {
    // Double-Checked Locking für maximale Performance ohne dauerhaftes Blockieren
    if (redisClient == null) {
      synchronized (JedisAdapter.class) {
        if (redisClient == null) {

          // Konfiguration des Verbindungspools
          ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
          poolConfig.setMaxTotal(maxConnections);
          poolConfig.setMaxIdle(maxConnections / 2);

          // Verhindert Fehler, falls maxConnections kleiner als 10 ist
          poolConfig.setMinIdle(Math.min(10, maxConnections));

          // Blockierungs-Schutz bei hoher Concurrency
          poolConfig.setBlockWhenExhausted(true);
          poolConfig.setMaxWait(Duration.ofMillis(3000));

          JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
            .password(password)
            .timeoutMillis(2000)
            .build();

          redisClient = RedisClient.builder()
            .poolConfig(poolConfig)
            .hostAndPort(host, port)
            .clientConfig(clientConfig)
            .build();
        }
      }
    }
  }

  public static RedisClient getClient() {
    RedisClient client = redisClient;
    if (client == null) {
      throw new IllegalStateException("JedisAdapter wurde noch nicht initialisiert!");
    }
    return client;
  }

  public static void destroy() {
    synchronized (JedisAdapter.class) {
      if (redisClient != null) {
        redisClient.close();
        redisClient = null;
      }
    }
  }
}


