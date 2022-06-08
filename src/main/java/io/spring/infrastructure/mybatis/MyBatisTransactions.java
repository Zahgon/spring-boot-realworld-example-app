package io.spring.infrastructure.mybatis;

import jakarta.inject.Singleton;
import java.util.function.Supplier;
import org.apache.ibatis.session.SqlSessionManager;

/**
 * Runs a unit of work in a single thread-bound MyBatis session. The session is committed when the
 * work completes and rolled back when it throws. Nested calls join the outer session.
 */
@Singleton
public class MyBatisTransactions {
  private final SqlSessionManager sessions;

  public MyBatisTransactions(SqlSessionManager sessions) {
    this.sessions = sessions;
  }

  public void inTransaction(Runnable work) {
    inTransaction(
        () -> {
          work.run();
          return null;
        });
  }

  public <T> T inTransaction(Supplier<T> work) {
    if (sessions.isManagedSessionStarted()) {
      return work.get();
    }
    sessions.startManagedSession(false);
    try {
      T result = work.get();
      sessions.commit(true);
      return result;
    } catch (RuntimeException | Error e) {
      sessions.rollback(true);
      throw e;
    } finally {
      sessions.close();
    }
  }
}
