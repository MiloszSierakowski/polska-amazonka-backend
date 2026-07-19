package pl.polskaamazonka.backend.scheduler;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresAdvisoryLockServiceTest {

    @Test
    void occupiedLockSkipsTask() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        Runnable task = mock(Runnable.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(false);

        boolean executed = new PostgresAdvisoryLockService(dataSource).executeWithLock(123L, task);

        assertFalse(executed);
        verify(task, never()).run();
    }

    @Test
    void lockIsReleasedWhenTaskThrows() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        Runnable task = mock(Runnable.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("failure")).when(task).run();

        assertThrows(
                IllegalStateException.class,
                () -> new PostgresAdvisoryLockService(dataSource).executeWithLock(123L, task)
        );

        verify(release).executeQuery();
    }
}
