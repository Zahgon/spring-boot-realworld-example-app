package io.spring.infrastructure.mybatis;

import io.micronaut.context.annotation.Factory;
import io.spring.infrastructure.DatabaseMigration;
import io.spring.infrastructure.mybatis.mapper.ArticleFavoriteMapper;
import io.spring.infrastructure.mybatis.mapper.ArticleMapper;
import io.spring.infrastructure.mybatis.mapper.CommentMapper;
import io.spring.infrastructure.mybatis.mapper.UserMapper;
import io.spring.infrastructure.mybatis.readservice.ArticleFavoritesReadService;
import io.spring.infrastructure.mybatis.readservice.ArticleReadService;
import io.spring.infrastructure.mybatis.readservice.CommentReadService;
import io.spring.infrastructure.mybatis.readservice.TagReadService;
import io.spring.infrastructure.mybatis.readservice.UserReadService;
import io.spring.infrastructure.mybatis.readservice.UserRelationshipQueryService;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/**
 * Plain MyBatis wiring: one {@link SqlSessionManager} shared by every mapper. Outside of {@link
 * MyBatisTransactions#inTransaction} each mapper call runs in its own committed session.
 */
@Factory
public class MyBatisConfig {

  @Singleton
  public SqlSessionFactory sqlSessionFactory(
      DataSource dataSource, MyBatisProperties properties, DatabaseMigration migration) {
    Environment environment = new Environment("default", new JdbcTransactionFactory(), dataSource);
    Configuration configuration = new Configuration(environment);
    configuration.setCacheEnabled(properties.isCacheEnabled());
    configuration.setDefaultStatementTimeout(properties.getDefaultStatementTimeout());
    configuration.setMapUnderscoreToCamelCase(properties.isMapUnderscoreToCamelCase());
    configuration.setUseGeneratedKeys(properties.isUseGeneratedKeys());
    if (properties.getTypeHandlersPackage() != null) {
      configuration.getTypeHandlerRegistry().register(properties.getTypeHandlersPackage());
    }
    for (String location : properties.getMapperLocations()) {
      for (String resource : resolveResources(location)) {
        parseMapper(configuration, resource);
      }
    }
    return new SqlSessionFactoryBuilder().build(configuration);
  }

  @Singleton
  public SqlSessionManager sqlSessionManager(SqlSessionFactory sqlSessionFactory) {
    return SqlSessionManager.newInstance(sqlSessionFactory);
  }

  @Singleton
  public ArticleMapper articleMapper(SqlSessionManager sessions) {
    return sessions.getMapper(ArticleMapper.class);
  }

  @Singleton
  public ArticleFavoriteMapper articleFavoriteMapper(SqlSessionManager sessions) {
    return sessions.getMapper(ArticleFavoriteMapper.class);
  }

  @Singleton
  public CommentMapper commentMapper(SqlSessionManager sessions) {
    return sessions.getMapper(CommentMapper.class);
  }

  @Singleton
  public UserMapper userMapper(SqlSessionManager sessions) {
    return sessions.getMapper(UserMapper.class);
  }

  @Singleton
  public ArticleReadService articleReadService(SqlSessionManager sessions) {
    return sessions.getMapper(ArticleReadService.class);
  }

  @Singleton
  public ArticleFavoritesReadService articleFavoritesReadService(SqlSessionManager sessions) {
    return sessions.getMapper(ArticleFavoritesReadService.class);
  }

  @Singleton
  public CommentReadService commentReadService(SqlSessionManager sessions) {
    return sessions.getMapper(CommentReadService.class);
  }

  @Singleton
  public TagReadService tagReadService(SqlSessionManager sessions) {
    return sessions.getMapper(TagReadService.class);
  }

  @Singleton
  public UserReadService userReadService(SqlSessionManager sessions) {
    return sessions.getMapper(UserReadService.class);
  }

  @Singleton
  public UserRelationshipQueryService userRelationshipQueryService(SqlSessionManager sessions) {
    return sessions.getMapper(UserRelationshipQueryService.class);
  }

  private static void parseMapper(Configuration configuration, String resource) {
    try (InputStream in = Resources.getResourceAsStream(resource)) {
      new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse mapper resource " + resource, e);
    }
  }

  static List<String> resolveResources(String location) {
    int slash = location.lastIndexOf('/');
    String directory = slash < 0 ? "" : location.substring(0, slash);
    String name = location.substring(slash + 1);
    if (!name.contains("*")) {
      return List.of(location);
    }
    Pattern pattern = Pattern.compile(globToRegex(name));
    List<String> resources = new ArrayList<>();
    try {
      for (String candidate : VFS.getInstance().list(directory)) {
        String candidateName = candidate.substring(candidate.lastIndexOf('/') + 1);
        if (candidate.equals(directory + "/" + candidateName)
            && pattern.matcher(candidateName).matches()) {
          resources.add(candidate);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list mapper resources in " + directory, e);
    }
    resources.sort(String::compareTo);
    return resources;
  }

  private static String globToRegex(String glob) {
    StringBuilder regex = new StringBuilder();
    for (char c : glob.toCharArray()) {
      if (c == '*') {
        regex.append("[^/]*");
      } else if (c == '?') {
        regex.append("[^/]");
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return regex.toString();
  }
}
