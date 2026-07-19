package pl.polskaamazonka.backend.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class PostgresAdvisoryLockService {

    static final long LINK_VALIDATION_LOCK_ID = 7_614_290_031L;

    private final DataSource dataSource;

    public boolean executeWithLock(long lockId, Runnable task) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAcquire(connection, lockId)) {
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                release(connection, lockId);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not manage scheduled task lock", exception);
        }
    }

    private boolean tryAcquire(Connection connection, long lockId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, lockId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private void release(Connection connection, long lockId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, lockId);
            statement.executeQuery();
        }
    }
}
