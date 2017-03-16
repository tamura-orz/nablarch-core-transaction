package nablarch.common.handler;

import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.transaction.TransactionContext;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;

/**
 *
 */
public class ConnectionManagementHandlerInTest implements Handler<Object, Object> {

    private ConnectionFactory connectionFactory;
    private String connectionName = TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY;

    @Override
    public Object handle(final Object o, final ExecutionContext context) {
        final TransactionManagerConnection connection = connectionFactory.getConnection(connectionName);
        DbConnectionContext.setConnection(connectionName, connection);
        try {
            return context.handleNext(o);
        } finally {
            DbConnectionContext.removeConnection(connectionName);
        }
    }

    public void setConnectionFactory(final ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void setConnectionName(final String connectionName) {
        this.connectionName = connectionName;
    }
}
